package lk.techmart.core.service;

import lk.techmart.core.entity.Order;

import java.util.List;
import java.util.Map;

public interface OrderQueryService {
    List<Order> getAllOrders(int offset, int limit);
    List<Order> getOrdersByStatus(String status, int offset, int limit);
    Order getOrderById(Integer orderId);
    void updateOrderStatus(Integer orderId, String newStatus, String comment);
    Map<String, Long> getOrderStats();
    List<Map<String, Object>> getOrderRows(String status, int offset, int limit);
    Map<String, Object> getOrderDetails(Integer orderId);
}
