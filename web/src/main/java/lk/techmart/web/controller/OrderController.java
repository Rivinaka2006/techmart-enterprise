package lk.techmart.web.controller;

import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lk.techmart.core.service.OrderQueryService;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderController {

    @EJB
    private OrderQueryService orderQueryService;

    @GET
    public Response getAllOrders(
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("50") int limit,
            @QueryParam("status") String status) {
        try {
            return Response.ok(orderQueryService.getOrderRows(status, offset, limit)).build();
        } catch (Exception e) {
            log.error("Error fetching orders", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/stats")
    public Response getStats() {
        try {
            Map<String, Long> stats = orderQueryService.getOrderStats();
            return Response.ok(stats).build();
        } catch (Exception e) {
            log.error("Error fetching order stats", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/{orderId}")
    public Response getOrderById(@PathParam("orderId") Integer orderId) {
        try {
            return Response.ok(orderQueryService.getOrderDetails(orderId)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Error fetching order {}", orderId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @PUT
    @Path("/{orderId}/status")
    public Response updateOrderStatus(
            @PathParam("orderId") Integer orderId,
            Map<String, String> payload) {
        try {
            String newStatus = payload.get("status");
            String comment = payload.get("comment");

            if (newStatus == null || newStatus.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Status is required"))
                        .build();
            }

            orderQueryService.updateOrderStatus(orderId, newStatus, comment);
            return Response.ok(Map.of("message", "Order status updated")).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Error updating order {} status", orderId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }
}
