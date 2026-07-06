package lk.techmart.ejb.service;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lk.techmart.core.entity.Order;
import lk.techmart.core.entity.OrderItem;
import lk.techmart.core.entity.OrderStatusHistory;
import lk.techmart.core.service.OrderQueryService;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Stateless
public class OrderQueryServiceImpl implements OrderQueryService {

    @PersistenceContext(unitName = "TechMartPU")
    private EntityManager em;

    @Override
    public List<Order> getAllOrders(int offset, int limit) {
        TypedQuery<Order> query = em.createQuery(
            "SELECT o FROM Order o ORDER BY o.createdAt DESC",
            Order.class
        );
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    @Override
    public Map<String, Long> getOrderStats() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        Long ordersToday = em.createQuery("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :start", Long.class)
                .setParameter("start", startOfDay)
                .getSingleResult();

        Long totalOrders = em.createQuery("SELECT COUNT(o) FROM Order o", Long.class)
                .getSingleResult();

        Long processing = em.createQuery("SELECT COUNT(o) FROM Order o WHERE o.status = :s", Long.class)
                .setParameter("s", "PROCESSING")
                .getSingleResult();

        Long completedToday = em.createQuery("SELECT COUNT(o) FROM Order o WHERE o.status = :s AND o.createdAt >= :start", Long.class)
                .setParameter("s", "COMPLETED")
                .setParameter("start", startOfDay)
                .getSingleResult();

        Long cancelled = em.createQuery("SELECT COUNT(o) FROM Order o WHERE o.status = :s", Long.class)
                .setParameter("s", "CANCELLED")
                .getSingleResult();

        Map<String, Long> stats = new HashMap<>();
        stats.put("totalOrders", totalOrders);
        stats.put("ordersToday", ordersToday);
        stats.put("processing", processing);
        stats.put("completedToday", completedToday);
        stats.put("cancelled", cancelled);
        return stats;
    }

    @Override
    public List<Order> getOrdersByStatus(String status, int offset, int limit) {
        TypedQuery<Order> query = em.createQuery(
            "SELECT o FROM Order o WHERE o.status = :status ORDER BY o.createdAt DESC",
            Order.class
        );
        query.setParameter("status", status);
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    @Override
    public Order getOrderById(Integer orderId) {
        Order order = em.find(Order.class, orderId);
        if (order != null) {
            
            order.getOrderItems().size();
            order.getStatusHistory().size();
        }
        return order;
    }

    @Override
    public void updateOrderStatus(Integer orderId, String newStatus, String comment) {
        Order order = em.find(Order.class, orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }

        String oldStatus = order.getStatus();
        order.setStatus(newStatus);

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedAt(LocalDateTime.now());
        history.setChangedBy("ADMIN");
        history.setNotes(comment);
        em.persist(history);

        log.info("Order {} status updated from {} to {} by admin", orderId, oldStatus, newStatus);
    }

    @Override
    public List<Map<String, Object>> getOrderRows(String status, int offset, int limit) {
        String jpql = "SELECT o.id, u.username, u.email, o.createdAt, o.status, o.totalAmount, COUNT(oi.id) " +
                "FROM Order o JOIN o.user u LEFT JOIN o.orderItems oi ";
        if (status != null && !status.isBlank()) {
            jpql += "WHERE o.status = :status ";
        }
        jpql += "GROUP BY o.id, u.username, u.email, o.createdAt, o.status, o.totalAmount " +
                "ORDER BY o.createdAt DESC";

        TypedQuery<Object[]> query = em.createQuery(jpql, Object[].class);
        if (status != null && !status.isBlank()) {
            query.setParameter("status", status.toUpperCase());
        }
        query.setFirstResult(offset);
        query.setMaxResults(limit);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] row : query.getResultList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row[0]);
            item.put("customerName", row[1]);
            item.put("customerEmail", row[2]);
            item.put("createdAt", row[3] == null ? null : row[3].toString());
            item.put("status", row[4]);
            item.put("totalAmount", row[5]);
            item.put("itemCount", ((Number) row[6]).intValue());
            rows.add(item);
        }
        return rows;
    }

    @Override
    public Map<String, Object> getOrderDetails(Integer orderId) {
        Order order = em.createQuery(
                        "SELECT o FROM Order o JOIN FETCH o.user WHERE o.id = :id",
                        Order.class)
                .setParameter("id", orderId)
                .getResultStream()
                .findFirst()
                .orElse(null);

        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }

        List<OrderItem> items = em.createQuery(
                        "SELECT oi FROM OrderItem oi JOIN FETCH oi.product WHERE oi.order.id = :id ORDER BY oi.id",
                        OrderItem.class)
                .setParameter("id", orderId)
                .getResultList();

        List<OrderStatusHistory> history = em.createQuery(
                        "SELECT h FROM OrderStatusHistory h WHERE h.order.id = :id ORDER BY h.changedAt DESC",
                        OrderStatusHistory.class)
                .setParameter("id", orderId)
                .getResultList();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", order.getId());
        details.put("customerName", order.getUser().getUsername());
        details.put("customerEmail", order.getUser().getEmail());
        details.put("createdAt", order.getCreatedAt() == null ? null : order.getCreatedAt().toString());
        details.put("status", order.getStatus());
        details.put("totalAmount", order.getTotalAmount());
        details.put("items", toItemRows(items));
        details.put("statusHistory", toHistoryRows(history));
        return details;
    }

    private List<Map<String, Object>> toItemRows(List<OrderItem> items) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (OrderItem item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productName", item.getProduct() == null ? "Unknown product" : item.getProduct().getName());
            row.put("quantity", item.getQuantity());
            row.put("pricePerUnit", item.getPricePerUnit());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> toHistoryRows(List<OrderStatusHistory> history) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (OrderStatusHistory item : history) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("changedAt", item.getChangedAt() == null ? null : item.getChangedAt().toString());
            row.put("oldStatus", item.getOldStatus());
            row.put("newStatus", item.getNewStatus());
            row.put("changedBy", item.getChangedBy());
            row.put("notes", item.getNotes());
            rows.add(row);
        }
        return rows;
    }
}
