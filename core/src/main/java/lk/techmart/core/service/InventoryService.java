package lk.techmart.core.service;

import lk.techmart.core.entity.InventoryTransaction;
import lk.techmart.core.entity.WarehouseStock;
import lk.techmart.core.entity.Product;
import lk.techmart.core.entity.Warehouse;

import java.util.List;
import java.util.Map;

public interface InventoryService {
    void deductStock(Integer productId, Integer warehouseId, int quantity);
    void restockProduct(Integer productId, Integer warehouseId, int quantity);
    List<WarehouseStock> getStockLevels(Integer warehouseId);
    List<InventoryTransaction> getRecentTransactions(int limit);
    List<Product> getLowStockProducts(int threshold);
    List<Product> getOutOfStockProducts();
    List<Warehouse> getAllWarehouses();
    WarehouseStock getStockByProductAndWarehouse(Integer productId, Integer warehouseId);
    Integer findWarehouseWithStock(Integer productId, int requiredQuantity);
    Map<String, Object> getInventoryDashboardData(int transactionLimit);
}
