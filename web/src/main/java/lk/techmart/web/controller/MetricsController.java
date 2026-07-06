package lk.techmart.web.controller;

import jakarta.ejb.EJB;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lk.techmart.core.service.MetricsQueryService;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@Path("/metrics")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class MetricsController {

    @EJB
    private MetricsQueryService metricsQueryService;

    @GET
    @Path("/performance")
    public Response getPerformanceMetrics(@QueryParam("limit") @DefaultValue("50") int limit) {
        return Response.ok(metricsQueryService.getRecentPerformanceMetrics(limit)).build();
    }

    @GET
    @Path("/messages")
    public Response getMessageLogs(@QueryParam("limit") @DefaultValue("50") int limit) {
        return Response.ok(metricsQueryService.getRecentMessageLogs(limit)).build();
    }

    @GET
    @Path("/messaging-dashboard")
    public Response getMessagingDashboard(@QueryParam("limit") @DefaultValue("50") int limit) {
        return Response.ok(metricsQueryService.getMessagingDashboardData(limit)).build();
    }

    @GET
    @Path("/monitoring-dashboard")
    public Response getMonitoringDashboard(@QueryParam("limit") @DefaultValue("250") int limit) {
        try {
            return Response.ok(metricsQueryService.getMonitoringDashboardData(limit)).build();
        } catch (Exception e) {
            log.error("Error loading monitoring dashboard", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/analytics-dashboard")
    public Response getAnalyticsDashboard(@QueryParam("hours") @DefaultValue("24") int hours) {
        try {
            return Response.ok(metricsQueryService.getAnalyticsDashboardData(hours)).build();
        } catch (Exception e) {
            log.error("Error loading analytics dashboard", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }
}
