package lk.techmart.web.controller;

import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lk.techmart.core.service.SessionQueryService;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@Path("/sessions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SessionController {

    @EJB
    private SessionQueryService sessionQueryService;

    @GET
    @Path("/counts")
    public Response getSessionCounts() {
        try {
            Map<String, Long> counts = sessionQueryService.getSessionCounts();
            return Response.ok(counts).build();
        } catch (Exception e) {
            log.error("Error fetching session counts", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/active")
    public Response getActiveSessions() {
        try {
            return Response.ok(sessionQueryService.getSessionDashboardData("ACTIVE", 0, 100).get("sessions")).build();
        } catch (Exception e) {
            log.error("Error fetching active sessions", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @GET
    public Response getAllSessions(
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("50") int limit,
            @QueryParam("status") String status) {
        try {
            return Response.ok(sessionQueryService.getSessionDashboardData(status, offset, limit).get("sessions")).build();
        } catch (Exception e) {
            log.error("Error fetching all sessions", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/dashboard")
    public Response getSessionDashboard(
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("50") int limit,
            @QueryParam("status") String status) {
        try {
            return Response.ok(sessionQueryService.getSessionDashboardData(status, offset, limit)).build();
        } catch (Exception e) {
            log.error("Error fetching session dashboard data", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/expire-idle")
    public Response expireIdleSessions(@QueryParam("timeoutMinutes") @DefaultValue("30") int timeoutMinutes) {
        try {
            sessionQueryService.expireIdleSessions();
            return Response.ok(Map.of("message", "Idle sessions expired")).build();
        } catch (Exception e) {
            log.error("Error expiring idle sessions", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }
}
