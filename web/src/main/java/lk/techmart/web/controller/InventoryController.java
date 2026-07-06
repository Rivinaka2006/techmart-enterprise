package lk.techmart.web.controller;

import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lk.techmart.core.entity.InventoryTransaction;
import lk.techmart.core.entity.Product;
import lk.techmart.core.entity.WarehouseStock;
import lk.techmart.core.service.InventoryService;
import lk.techmart.core.service.ProductService;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Path("/inventory")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InventoryController {

    @EJB
    private InventoryService inventoryService;

    @EJB(beanName = "ProductSessionBean")
    private ProductService productService;

    @GET
    @Path("/meta")
    public Response getInventoryMeta() {
        try {
            return Response.ok(Map.of(
                    "products", productService.getAllProducts(),
                    "warehouses", inventoryService.getAllWarehouses()
            )).build();
        } catch (Exception e) {
            log.error("Error fetching inventory metadata", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/warehouse/{warehouseId}")
    public Response getStockLevels(@PathParam("warehouseId") Integer warehouseId) {
        try {
            List<WarehouseStock> stocks = inventoryService.getStockLevels(warehouseId);
            return Response.ok(stocks).build();
        } catch (Exception e) {
            log.error("Error fetching stock levels for warehouse {}", warehouseId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/low-stock")
    public Response getLowStockProducts(@QueryParam("threshold") @DefaultValue("10") int threshold) {
        try {
            List<Product> products = inventoryService.getLowStockProducts(threshold);
            return Response.ok(products).build();
        } catch (Exception e) {
            log.error("Error fetching low stock products", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/restock")
    public Response restockProduct(
            @QueryParam("productId") Integer productId,
            @QueryParam("warehouseId") Integer warehouseId,
            @QueryParam("quantity") int quantity) {
        try {
            inventoryService.restockProduct(productId, warehouseId, quantity);
            return Response.ok(Map.of(
                    "message", "Product restocked successfully",
                    "productId", productId,
                    "warehouseId", warehouseId,
                    "quantity", quantity
            )).build();
        } catch (Exception e) {
            log.error("Error restocking product {} in warehouse {}", productId, warehouseId, e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/transactions")
    public Response getInventoryTransactions(@QueryParam("limit") @DefaultValue("10") int limit) {
        try {
            List<InventoryTransaction> transactions = inventoryService.getRecentTransactions(limit);
            return Response.ok(transactions).build();
        } catch (Exception e) {
            log.error("Error fetching inventory transactions", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/summary")
    public Response getInventorySummary() {
        try {
            
            List<Product> lowStock = inventoryService.getLowStockProducts(10);
            List<Product> outOfStock = inventoryService.getOutOfStockProducts();

            Map<String, Object> summary = new HashMap<>();
            summary.put("lowStockCount", lowStock.size());
            summary.put("lowStockProducts", lowStock);
            summary.put("outOfStockCount", outOfStock.size());
            summary.put("outOfStockProducts", outOfStock);

            return Response.ok(summary).build();
        } catch (Exception e) {
            log.error("Error fetching inventory summary", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/dashboard")
    public Response getInventoryDashboard(@QueryParam("transactionLimit") @DefaultValue("10") int transactionLimit) {
        try {
            return Response.ok(inventoryService.getInventoryDashboardData(transactionLimit)).build();
        } catch (Exception e) {
            log.error("Error fetching inventory dashboard data", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }
}
