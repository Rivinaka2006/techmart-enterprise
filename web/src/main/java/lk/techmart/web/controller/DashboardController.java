package lk.techmart.web.controller;

import jakarta.ejb.EJB;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lk.techmart.core.entity.MessageLog;
import lk.techmart.core.entity.PerformanceMetric;
import lk.techmart.core.service.MetricsQueryService;
import lk.techmart.core.service.OrderQueryService;
import lk.techmart.core.service.ProductService;
import lk.techmart.core.service.SessionQueryService;
import lk.techmart.core.service.SystemHealthService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Path("/dashboard")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class DashboardController {

    private static final DateTimeFormatter TIME_LABEL = DateTimeFormatter.ofPattern("HH:mm");

    @EJB(beanName = "ProductSessionBean")
    private ProductService productService;

    @EJB
    private OrderQueryService orderQueryService;

    @EJB
    private SessionQueryService sessionQueryService;

    @EJB
    private MetricsQueryService metricsQueryService;

    @EJB
    private SystemHealthService healthService;

    @GET
    @Path("/summary")
    public Response getSummary() {
        try {
            Map<String, Object> summary = new LinkedHashMap<>();

            Map<String, Long> orderStats = orderQueryService.getOrderStats();
            Map<String, Long> sessionCounts = sessionQueryService.getSessionCounts();
            List<MessageLog> messageLogs = metricsQueryService.getRecentMessageLogs(500);
            List<PerformanceMetric> performanceMetrics = metricsQueryService.getRecentPerformanceMetrics(500);
            Map<String, Long> health = healthService.getAllCounters();

            summary.put("totalProducts", productService.countProducts());
            summary.put("totalOrders", value(orderStats, "totalOrders"));
            summary.put("ordersToday", value(orderStats, "ordersToday"));
            summary.put("activeSessions", value(sessionCounts, "active"));
            summary.put("messagesProcessed", countSuccessfulMessages(messageLogs));
            summary.put("avgResponseTimeMs", averagePerformanceMs(performanceMetrics));
            summary.put("systemUptimeSeconds", value(health, "uptimeSeconds"));
            summary.put("orderStats", orderStats);
            summary.put("sessionCounts", sessionCounts);
            summary.put("health", health);

            return Response.ok(summary).build();
        } catch (Exception e) {
            log.error("Error loading dashboard summary", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/charts")
    public Response getCharts() {
        try {
            List<MessageLog> messageLogs = metricsQueryService.getRecentMessageLogs(100);
            List<PerformanceMetric> performanceMetrics = metricsQueryService.getRecentPerformanceMetrics(100);

            Map<String, Object> charts = new LinkedHashMap<>();
            charts.put("jmsThroughput", buildMessageSeries(messageLogs));
            charts.put("responseTime", buildPerformanceSeries(performanceMetrics));
            charts.put("systemCounters", buildSystemCounterSeries(performanceMetrics));
            charts.put("messageStatus", buildMessageStatus(messageLogs));

            return Response.ok(charts).build();
        } catch (Exception e) {
            log.error("Error loading dashboard charts", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    private long value(Map<String, Long> source, String key) {
        Long value = source.get(key);
        return value == null ? 0L : value;
    }

    private long countSuccessfulMessages(List<MessageLog> logs) {
        return logs.stream()
                .filter(log -> !"FAILED".equalsIgnoreCase(log.getStatus()))
                .count();
    }

    private long averagePerformanceMs(List<PerformanceMetric> metrics) {
        List<BigDecimal> values = metrics.stream()
                .filter(metric -> metric.getMetricName() != null && !metric.getMetricName().startsWith("system."))
                .map(PerformanceMetric::getMetricValue)
                .toList();

        if (values.isEmpty()) return 0L;

        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(values.size()), 0, RoundingMode.HALF_UP).longValue();
    }

    private Map<String, Object> buildMessageSeries(List<MessageLog> logs) {
        List<MessageLog> ordered = new ArrayList<>(logs);
        Collections.reverse(ordered);

        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();

        for (MessageLog log : ordered) {
            labels.add(formatTime(log.getCreatedAt()));
            values.add(log.getProcessingTimeMs() == null ? 0 : log.getProcessingTimeMs());
        }

        return series(labels, values);
    }

    private Map<String, Object> buildPerformanceSeries(List<PerformanceMetric> metrics) {
        List<PerformanceMetric> ordered = metrics.stream()
                .filter(metric -> metric.getMetricName() != null && !metric.getMetricName().startsWith("system."))
                .toList();
        ordered = new ArrayList<>(ordered);
        Collections.reverse(ordered);

        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();

        for (PerformanceMetric metric : ordered) {
            labels.add(formatTime(metric.getRecordedAt()));
            values.add(metric.getMetricValue());
        }

        return series(labels, values);
    }

    private Map<String, Object> buildSystemCounterSeries(List<PerformanceMetric> metrics) {
        Map<String, List<Object>> counters = new LinkedHashMap<>();
        counters.put("system.ejb_invocations", new ArrayList<>());
        counters.put("system.mdb_processing", new ArrayList<>());
        counters.put("system.async_tasks", new ArrayList<>());
        counters.put("system.db_queries", new ArrayList<>());

        List<PerformanceMetric> ordered = new ArrayList<>(metrics);
        Collections.reverse(ordered);

        for (PerformanceMetric metric : ordered) {
            List<Object> values = counters.get(metric.getMetricName());
            if (values != null) {
                values.add(metric.getMetricValue());
            }
        }

        return Map.of(
                "ejbInvocations", counters.get("system.ejb_invocations"),
                "mdbProcessing", counters.get("system.mdb_processing"),
                "asyncTasks", counters.get("system.async_tasks"),
                "dbQueries", counters.get("system.db_queries")
        );
    }

    private Map<String, Long> buildMessageStatus(List<MessageLog> logs) {
        Map<String, Long> status = new LinkedHashMap<>();
        status.put("SUCCESS", 0L);
        status.put("FAILED", 0L);

        for (MessageLog log : logs) {
            String key = log.getStatus() == null ? "UNKNOWN" : log.getStatus().toUpperCase();
            status.put(key, status.getOrDefault(key, 0L) + 1L);
        }

        return status;
    }

    private Map<String, Object> series(List<String> labels, List<?> values) {
        Map<String, Object> series = new LinkedHashMap<>();
        series.put("labels", labels);
        series.put("values", values);
        return series;
    }

    private String formatTime(LocalDateTime dateTime) {
        return dateTime == null ? "--:--" : TIME_LABEL.format(dateTime);
    }
}
