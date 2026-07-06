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
import jakarta.persistence.TypedQuery;
import lk.techmart.core.dto.NotificationEvent;
import lk.techmart.core.entity.*;
import lk.techmart.core.service.InventoryService;
import lk.techmart.core.service.SystemHealthService;
import lk.techmart.ejb.interceptor.PerformanceMonitor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Stateless
@Interceptors(PerformanceMonitor.class)
public class InventoryServiceImpl implements InventoryService {

    @PersistenceContext(unitName = "TechMartPU")
    private EntityManager em;

    @Inject
    private JMSContext jmsContext;

    @Resource(lookup = "jms/topic/InventoryUpdatesTopic")
    private Topic inventoryTopic;

    @EJB
    private SystemHealthService healthService;

    private static final int LOW_STOCK_THRESHOLD = 10;

    @Override
    public void deductStock(Integer productId, Integer warehouseId, int quantity) {
        WarehouseStock stock = getStockByProductAndWarehouse(productId, warehouseId);

        if (stock == null) {
            throw new IllegalStateException("No stock record found for product " + productId + " in warehouse " + warehouseId);
        }

        if (stock.getQuantity() < quantity) {
            throw new IllegalStateException("Insufficient stock: available=" + stock.getQuantity() + ", required=" + quantity);
        }

        stock.setQuantity(stock.getQuantity() - quantity);
        stock.setLastUpdated(LocalDateTime.ofInstant(Instant.now(), java.time.ZoneId.systemDefault()));
        em.merge(stock);

        
        InventoryTransaction txn = new InventoryTransaction();
        txn.setProduct(em.getReference(Product.class, productId));
        txn.setWarehouse(em.getReference(Warehouse.class, warehouseId));
        txn.setQuantityChange(-quantity);
        txn.setTransactionType("ORDER_DEDUCTION");
        txn.setCreatedAt(LocalDateTime.now());
        em.persist(txn);

        
        if (stock.getQuantity() <= LOW_STOCK_THRESHOLD) {
            publishLowStockEvent(productId, stock.getQuantity());
        }

        healthService.incrementDatabaseQuery();
        log.info("Deducted {} units of product {} from warehouse {}, remaining: {}", 
                 quantity, productId, warehouseId, stock.getQuantity());
    }

    @Override
    public void restockProduct(Integer productId, Integer warehouseId, int quantity) {
        if (productId == null) throw new IllegalArgumentException("Product is required");
        if (warehouseId == null) throw new IllegalArgumentException("Warehouse is required");
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be greater than zero");

        Product product = em.find(Product.class, productId);
        if (product == null) throw new IllegalArgumentException("Unknown product: " + productId);

        Warehouse warehouse = em.find(Warehouse.class, warehouseId);
        if (warehouse == null) throw new IllegalArgumentException("Unknown warehouse: " + warehouseId);

        WarehouseStock stock = getStockByProductAndWarehouse(productId, warehouseId);

        if (stock == null) {
            
            stock = new WarehouseStock();
            WarehouseStockId stockId = new WarehouseStockId();
            stockId.setWarehouseId(warehouseId);
            stockId.setProductId(productId);
            stock.setId(stockId);
            stock.setWarehouse(warehouse);
            stock.setProduct(product);
            stock.setQuantity(quantity);
            stock.setLastUpdated(LocalDateTime.ofInstant(Instant.now(), java.time.ZoneId.systemDefault()));
            em.persist(stock);
        } else {
            stock.setQuantity(stock.getQuantity() + quantity);
            stock.setLastUpdated(LocalDateTime.ofInstant(Instant.now(), java.time.ZoneId.systemDefault()));
            em.merge(stock);
        }

        
        InventoryTransaction txn = new InventoryTransaction();
        txn.setProduct(em.getReference(Product.class, productId));
        txn.setWarehouse(em.getReference(Warehouse.class, warehouseId));
        txn.setQuantityChange(quantity);
        txn.setTransactionType("RESTOCK");
        txn.setCreatedAt(LocalDateTime.now());
        em.persist(txn);

        healthService.incrementDatabaseQuery();
        log.info("Restocked {} units of product {} to warehouse {}, new total: {}", 
                 quantity, productId, warehouseId, stock.getQuantity());
    }

    @Override
    public List<WarehouseStock> getStockLevels(Integer warehouseId) {
        TypedQuery<WarehouseStock> query = em.createQuery(
            "SELECT ws FROM WarehouseStock ws WHERE ws.warehouse.id = :warehouseId ORDER BY ws.product.name",
            WarehouseStock.class
        );
        query.setParameter("warehouseId", warehouseId);
        healthService.incrementDatabaseQuery();
        return query.getResultList();
    }

    @Override
    public List<InventoryTransaction> getRecentTransactions(int limit) {
        TypedQuery<InventoryTransaction> query = em.createQuery(
            "SELECT tx FROM InventoryTransaction tx JOIN FETCH tx.product p JOIN FETCH tx.warehouse w ORDER BY tx.createdAt DESC",
            InventoryTransaction.class
        );
        query.setMaxResults(limit);
        healthService.incrementDatabaseQuery();
        return query.getResultList();
    }

    @Override
    public List<Product> getLowStockProducts(int threshold) {
        TypedQuery<Product> query = em.createQuery(
            "SELECT DISTINCT ws.product FROM WarehouseStock ws WHERE ws.quantity <= :threshold ORDER BY ws.quantity",
            Product.class
        );
        query.setParameter("threshold", threshold);
        healthService.incrementDatabaseQuery();
        return query.getResultList();
    }

    @Override
    public List<Product> getOutOfStockProducts() {
        TypedQuery<Product> query = em.createQuery(
            "SELECT DISTINCT ws.product FROM WarehouseStock ws WHERE ws.quantity <= 0 ORDER BY ws.product.name",
            Product.class
        );
        healthService.incrementDatabaseQuery();
        return query.getResultList();
    }

    @Override
    public List<Warehouse> getAllWarehouses() {
        TypedQuery<Warehouse> query = em.createQuery(
                "SELECT w FROM Warehouse w ORDER BY w.warehouseName",
                Warehouse.class
        );
        healthService.incrementDatabaseQuery();
        return query.getResultList();
    }

    @Override
    public WarehouseStock getStockByProductAndWarehouse(Integer productId, Integer warehouseId) {
        TypedQuery<WarehouseStock> query = em.createQuery(
            "SELECT ws FROM WarehouseStock ws WHERE ws.product.id = :productId AND ws.warehouse.id = :warehouseId",
            WarehouseStock.class
        );
        query.setParameter("productId", productId);
        query.setParameter("warehouseId", warehouseId);
        List<WarehouseStock> results = query.getResultList();
        healthService.incrementDatabaseQuery();
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public Integer findWarehouseWithStock(Integer productId, int requiredQuantity) {
        TypedQuery<Integer> query = em.createQuery(
            "SELECT ws.warehouse.id FROM WarehouseStock ws WHERE ws.product.id = :productId AND ws.quantity >= :required ORDER BY ws.quantity DESC",
            Integer.class
        );
        query.setParameter("productId", productId);
        query.setParameter("required", requiredQuantity);
        query.setMaxResults(1);
        List<Integer> results = query.getResultList();
        healthService.incrementDatabaseQuery();
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public Map<String, Object> getInventoryDashboardData(int transactionLimit) {
        Map<String, Object> dashboard = new LinkedHashMap<>();

        Long totalSkus = em.createQuery("SELECT COUNT(p) FROM Product p", Long.class)
                .getSingleResult();
        Long totalWarehouses = em.createQuery("SELECT COUNT(w) FROM Warehouse w", Long.class)
                .getSingleResult();
        Long stockRecords = em.createQuery("SELECT COUNT(ws) FROM WarehouseStock ws", Long.class)
                .getSingleResult();

        List<Map<String, Object>> lowStockProducts = getProductStockSummaries(LOW_STOCK_THRESHOLD);
        List<Map<String, Object>> outOfStockProducts = getProductStockSummaries(0);
        List<Map<String, Object>> warehouses = getWarehouseSummaries();
        List<Map<String, Object>> transactions = getTransactionSummaries(transactionLimit);

        dashboard.put("totalSkus", totalSkus);
        dashboard.put("totalWarehouses", totalWarehouses);
        dashboard.put("stockRecords", stockRecords);
        dashboard.put("lowStockCount", lowStockProducts.size());
        dashboard.put("outOfStockCount", outOfStockProducts.size());
        dashboard.put("lowStockProducts", lowStockProducts.stream().limit(10).toList());
        dashboard.put("outOfStockProducts", outOfStockProducts);
        dashboard.put("warehouses", warehouses);
        dashboard.put("transactions", transactions);
        dashboard.put("lastUpdated", LocalDateTime.now().toString());

        healthService.incrementDatabaseQuery();
        return dashboard;
    }

    private List<Map<String, Object>> getProductStockSummaries(int threshold) {
        List<Object[]> rows = em.createQuery(
                "SELECT p.id, p.name, COALESCE(SUM(ws.quantity), 0) " +
                        "FROM Product p LEFT JOIN p.warehouseStocks ws " +
                        "GROUP BY p.id, p.name " +
                        "HAVING COALESCE(SUM(ws.quantity), 0) <= :threshold " +
                        "ORDER BY COALESCE(SUM(ws.quantity), 0), p.name",
                Object[].class)
                .setParameter("threshold", threshold)
                .getResultList();

        List<Map<String, Object>> products = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> product = new LinkedHashMap<>();
            product.put("id", row[0]);
            product.put("name", row[1]);
            product.put("quantity", ((Number) row[2]).intValue());
            product.put("threshold", LOW_STOCK_THRESHOLD);
            products.add(product);
        }
        return products;
    }

    private List<Map<String, Object>> getWarehouseSummaries() {
        List<Object[]> rows = em.createQuery(
                "SELECT w.id, w.warehouseName, w.location, COUNT(ws), COALESCE(SUM(ws.quantity), 0), " +
                        "SUM(CASE WHEN ws.quantity <= :threshold THEN 1 ELSE 0 END) " +
                        "FROM Warehouse w LEFT JOIN WarehouseStock ws ON ws.warehouse = w " +
                        "GROUP BY w.id, w.warehouseName, w.location " +
                        "ORDER BY w.warehouseName",
                Object[].class)
                .setParameter("threshold", LOW_STOCK_THRESHOLD)
                .getResultList();

        List<Map<String, Object>> warehouses = new ArrayList<>();
        for (Object[] row : rows) {
            int stockRecords = ((Number) row[3]).intValue();
            int totalUnits = ((Number) row[4]).intValue();
            int lowStockRecords = row[5] == null ? 0 : ((Number) row[5]).intValue();

            Map<String, Object> warehouse = new LinkedHashMap<>();
            warehouse.put("id", row[0]);
            warehouse.put("name", row[1]);
            warehouse.put("location", row[2]);
            warehouse.put("stockRecords", stockRecords);
            warehouse.put("totalUnits", totalUnits);
            warehouse.put("lowStockRecords", lowStockRecords);
            warehouse.put("healthPercent", calculateWarehouseHealth(stockRecords, lowStockRecords));
            warehouses.add(warehouse);
        }
        return warehouses;
    }

    private int calculateWarehouseHealth(int stockRecords, int lowStockRecords) {
        if (stockRecords <= 0) return 0;
        return Math.max(0, Math.round(((stockRecords - lowStockRecords) * 100f) / stockRecords));
    }

    private List<Map<String, Object>> getTransactionSummaries(int limit) {
        List<InventoryTransaction> recentTransactions = getRecentTransactions(Math.max(1, limit));
        List<Map<String, Object>> transactions = new ArrayList<>();

        for (InventoryTransaction txn : recentTransactions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", txn.getId());
            item.put("createdAt", txn.getCreatedAt() == null ? null : txn.getCreatedAt().toString());
            item.put("productName", txn.getProduct() == null ? "Unknown product" : txn.getProduct().getName());
            item.put("warehouseName", txn.getWarehouse() == null ? "Unknown warehouse" : txn.getWarehouse().getWarehouseName());
            item.put("transactionType", txn.getTransactionType());
            item.put("quantityChange", txn.getQuantityChange());
            transactions.add(item);
        }

        return transactions;
    }

    private void publishLowStockEvent(Integer productId, int remainingQuantity) {
        try {
            NotificationEvent event = new NotificationEvent(
                "LOW_STOCK",
                productId,
                "Product " + productId + " has low stock: " + remainingQuantity + " units remaining",
                Instant.now(),
                "{\"threshold\":" + LOW_STOCK_THRESHOLD + ",\"remaining\":" + remainingQuantity + "}"
            );
            jmsContext.createProducer().send(inventoryTopic, event);
            log.info("Published LOW_STOCK event for product {}", productId);
        } catch (Exception e) {
            log.error("Failed to publish low-stock notification", e);
        }
    }
}
