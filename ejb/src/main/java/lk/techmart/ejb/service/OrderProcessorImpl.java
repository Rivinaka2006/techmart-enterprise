package lk.techmart.ejb.service;

import jakarta.annotation.Resource;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.jms.JMSContext;
import jakarta.jms.Topic;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lk.techmart.core.dto.CartItem;
import lk.techmart.core.dto.NotificationEvent;
import lk.techmart.core.dto.OrderPayload;
import lk.techmart.core.entity.*;
import lk.techmart.core.service.AsyncTaskService;
import lk.techmart.core.service.InventoryService;
import lk.techmart.core.service.OrderProcessor;
import lk.techmart.ejb.interceptor.PerformanceMonitor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Stateless
@Interceptors(PerformanceMonitor.class) 
public class OrderProcessorImpl implements OrderProcessor {

    @PersistenceContext(unitName = "TechMartPU")
    private EntityManager em;

    @EJB(beanName = "AsyncTaskServiceImpl")
    private AsyncTaskService asyncTaskService;

    @EJB
    private InventoryService inventoryService;

    @Inject
    private JMSContext jmsContext;

    @Resource(lookup = "jms/topic/InventoryUpdatesTopic")
    private Topic inventoryTopic;

    @Override
    public void processOrder(OrderPayload payload) {
        Order order = new Order();
        order.setUser(em.getReference(User.class, payload.getUserId()));
        order.setTotalAmount(payload.getTotalAmount());
        order.setStatus("PROCESSING");
        order.setCreatedAt(LocalDateTime.ofInstant(payload.getSubmittedAt(), ZoneId.systemDefault()));
        em.persist(order);

        logStatusChange(order, null, "PROCESSING");

        for (CartItem ci : payload.getItems()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(em.getReference(Product.class, ci.getProductId()));
            orderItem.setQuantity(ci.getQuantity());
            orderItem.setPricePerUnit(ci.getPricePerUnit());
            em.persist(orderItem);

            
            
            Integer warehouseId = inventoryService.findWarehouseWithStock(ci.getProductId(), ci.getQuantity());
            if (warehouseId != null) {
                inventoryService.deductStock(ci.getProductId(), warehouseId, ci.getQuantity());
                log.info("Deducted {} units of product {} from warehouse {}", ci.getQuantity(), ci.getProductId(), warehouseId);
            } else {
                
                
                log.warn("No warehouse found with {} units of product {}, creating audit transaction only", ci.getQuantity(), ci.getProductId());
                InventoryTransaction inv = new InventoryTransaction();
                inv.setProduct(em.getReference(Product.class, ci.getProductId()));
                inv.setQuantityChange(-ci.getQuantity());
                inv.setTransactionType("ORDER_DEDUCTION_NO_WAREHOUSE");
                inv.setCreatedAt(LocalDateTime.now());
                em.persist(inv);
            }
        }

        order.setStatus("COMPLETED");
        logStatusChange(order, "PROCESSING", "COMPLETED");

        
        publishOrderCompletionEvent(order);

        
        asyncTaskService.generateInvoiceAsync(order.getId());

        log.info("Order {} processed for user {}", order.getId(), payload.getUserId());
    }

    private void logStatusChange(Order order, String oldStatus, String newStatus) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedAt(LocalDateTime.now());
        em.persist(history);
    }

    private void publishOrderCompletionEvent(Order order) {
        try {
            NotificationEvent event = new NotificationEvent(
                "ORDER_COMPLETED",
                order.getId(),
                "Order #" + order.getId() + " completed successfully",
                Instant.now(),
                "{\"userId\":" + order.getUser().getId() + ",\"amount\":" + order.getTotalAmount() + "}"
            );
            jmsContext.createProducer().send(inventoryTopic, event);
            log.info("Published ORDER_COMPLETED event for order {}", order.getId());
        } catch (Exception e) {
            log.error("Failed to publish order completion notification", e);
        }
    }
}