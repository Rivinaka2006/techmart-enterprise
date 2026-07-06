package lk.techmart.web.controller;

import jakarta.ejb.EJB;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lk.techmart.core.entity.MessageLog;
import lk.techmart.core.service.MetricsQueryService;
import lk.techmart.core.service.SessionQueryService;
import lk.techmart.core.service.SystemHealthService;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.naming.InitialContext;
import javax.naming.NamingException;

@Slf4j
@Path("/settings")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class SettingsController {

    private static final String DATASOURCE_JNDI = "jdbc/TechMartDS";
    private static final String ORDER_QUEUE_JNDI = "jms/queue/OrderQueue";
    private static final String INVENTORY_TOPIC_JNDI = "jms/topic/InventoryUpdatesTopic";

    @EJB
    private MetricsQueryService metricsQueryService;

    @EJB
    private SessionQueryService sessionQueryService;

    @EJB
    private SystemHealthService healthService;

    @GET
    @Path("/runtime")
    public Response getRuntimeSettings() {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            List<MessageLog> logs = metricsQueryService.getRecentMessageLogs(500);
            Map<String, Object> database = buildDatabaseSettings();
            Map<String, Object> jms = buildJmsSettings(logs);

            data.put("database", database);
            data.put("jms", jms);
            data.put("sessions", buildSessionSettings());
            data.put("notifications", buildNotificationSettings(logs));
            data.put("thresholds", buildThresholdSettings());
            data.put("status", buildStatusSummary(logs, database, jms));

            return Response.ok(data).build();
        } catch (Exception e) {
            log.error("Error loading runtime settings", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    private Map<String, Object> buildDatabaseSettings() {
        Map<String, Object> database = new LinkedHashMap<>();
        database.put("persistenceUnit", "TechMartPU");
        database.put("provider", "org.eclipse.persistence.jpa.PersistenceProvider");
        database.put("jtaDataSource", DATASOURCE_JNDI);
        database.put("schemaGeneration", "none");
        database.put("loggingLevel", "FINE");
        database.put("connected", false);

        try {
            DataSource dataSource = (DataSource) lookup(DATASOURCE_JNDI);
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metadata = connection.getMetaData();
                database.put("connected", true);
                database.put("productName", metadata.getDatabaseProductName());
                database.put("productVersion", metadata.getDatabaseProductVersion());
                database.put("driverName", metadata.getDriverName());
                database.put("driverVersion", metadata.getDriverVersion());
                database.put("url", sanitizeJdbcUrl(metadata.getURL()));
                database.put("userName", metadata.getUserName());
            }
        } catch (NamingException | SQLException | ClassCastException e) {
            database.put("error", e.getMessage());
        }
        return database;
    }

    private Map<String, Object> buildJmsSettings(List<MessageLog> logs) {
        Map<String, Object> jms = new LinkedHashMap<>();
        jms.put("orderQueue", Map.of(
                "name", "OrderQueue",
                "jndiName", ORDER_QUEUE_JNDI,
                "destinationType", "jakarta.jms.Queue",
                "producer", "ShoppingCartBean.checkout",
                "consumer", "OrderMDB",
                "acknowledgeMode", "Auto-acknowledge",
                "available", jndiAvailable(ORDER_QUEUE_JNDI),
                "messagesRecorded", countMessageType(logs, "ORDER_SUBMIT")
        ));
        jms.put("inventoryTopic", Map.of(
                "name", "InventoryUpdatesTopic",
                "jndiName", INVENTORY_TOPIC_JNDI,
                "destinationType", "jakarta.jms.Topic",
                "producer", "InventoryServiceImpl / OrderProcessorImpl",
                "available", jndiAvailable(INVENTORY_TOPIC_JNDI),
                "messagesRecorded", countPrefix(logs, "TOPIC_")
        ));
        jms.put("subscribers", List.of(
                Map.of(
                        "bean", "EmailNotificationMDB",
                        "clientId", "EmailNotificationClient",
                        "subscriptionName", "EmailSubscription",
                        "durability", "Durable",
                        "messagesRecorded", countMessageType(logs, "TOPIC_EMAIL")
                ),
                Map.of(
                        "bean", "PushNotificationMDB",
                        "clientId", "PushNotificationClient",
                        "subscriptionName", "PushSubscription",
                        "durability", "NonDurable",
                        "messagesRecorded", countMessageType(logs, "TOPIC_PUSH")
                )
        ));
        return jms;
    }

    private Map<String, Object> buildSessionSettings() {
        Map<String, Long> counts = sessionQueryService.getSessionCounts();
        Map<String, Long> health = healthService.getAllCounters();

        Map<String, Object> sessions = new LinkedHashMap<>();
        sessions.put("cartStatefulTimeoutMinutes", 30);
        sessions.put("trackingSource", "user_sessions");
        sessions.put("activeSessions", counts.getOrDefault("active", 0L));
        sessions.put("idleSessions", counts.getOrDefault("idle", 0L));
        sessions.put("expiredSessions", counts.getOrDefault("expired", 0L));
        sessions.put("uptimeSeconds", health.getOrDefault("uptimeSeconds", 0L));
        sessions.put("autoExpireEndpoint", "/api/sessions/expire-idle");
        return sessions;
    }

    private Map<String, Object> buildNotificationSettings(List<MessageLog> logs) {
        long emailTotal = countMessageType(logs, "EMAIL_GATEWAY");
        long pushTotal = countMessageType(logs, "PUSH_GATEWAY");
        long emailFailures = countFailures(logs, "EMAIL_GATEWAY");
        long pushFailures = countFailures(logs, "PUSH_GATEWAY");

        Map<String, Object> notifications = new LinkedHashMap<>();
        notifications.put("emailGateway", gateway("EmailGateway", emailTotal, emailFailures));
        notifications.put("pushGateway", gateway("PushGateway", pushTotal, pushFailures));
        notifications.put("circuitBreakerFailureThreshold", 3);
        notifications.put("circuitBreakerResetTimeoutSeconds", 30);
        notifications.put("adminEmailRecipient", "admin@techmart.lk");
        notifications.put("adminPushUser", "admin");
        return notifications;
    }

    private Map<String, Object> buildThresholdSettings() {
        Map<String, Object> thresholds = new LinkedHashMap<>();
        thresholds.put("resourceHighLoadPercent", 75);
        thresholds.put("resourceCriticalPercent", 90);
        thresholds.put("inventoryLowStockThreshold", 10);
        thresholds.put("cartTimeoutMinutes", 30);
        thresholds.put("gatewayFailureThreshold", 3);
        thresholds.put("gatewayResetTimeoutSeconds", 30);
        return thresholds;
    }

    private Map<String, Object> buildStatusSummary(List<MessageLog> logs, Map<String, Object> database, Map<String, Object> jms) {
        boolean dbConnected = Boolean.TRUE.equals(database.get("connected"));
        boolean orderQueueAvailable = nestedBoolean(jms, "orderQueue", "available");
        boolean topicAvailable = nestedBoolean(jms, "inventoryTopic", "available");
        long notificationFailures = countFailures(logs, "EMAIL_GATEWAY") + countFailures(logs, "PUSH_GATEWAY");

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("database", dbConnected ? "Validated" : "Unavailable");
        status.put("jmsBroker", orderQueueAvailable && topicAvailable ? "Validated" : "Unavailable");
        status.put("sessionStore", "Validated");
        status.put("notificationChannels", notificationFailures == 0 ? "Healthy" : "Attention");
        return status;
    }

    private boolean nestedBoolean(Map<String, Object> source, String mapKey, String valueKey) {
        Object value = source.get(mapKey);
        if (!(value instanceof Map<?, ?> map)) return false;
        return Boolean.TRUE.equals(map.get(valueKey));
    }

    private Map<String, Object> gateway(String name, long total, long failures) {
        Map<String, Object> gateway = new LinkedHashMap<>();
        gateway.put("name", name);
        gateway.put("totalCalls", total);
        gateway.put("failures", failures);
        gateway.put("successRate", total == 0 ? 0 : Math.round(((total - failures) * 10000.0) / total) / 100.0);
        return gateway;
    }

    private Object lookup(String jndiName) throws NamingException {
        InitialContext context = new InitialContext();
        try {
            return context.lookup(jndiName);
        } catch (NamingException first) {
            return context.lookup("java:comp/env/" + jndiName);
        }
    }

    private boolean jndiAvailable(String jndiName) {
        try {
            lookup(jndiName);
            return true;
        } catch (NamingException e) {
            return false;
        }
    }

    private long countMessageType(List<MessageLog> logs, String messageType) {
        return logs.stream()
                .filter(log -> messageType.equals(log.getMessageType()))
                .count();
    }

    private long countPrefix(List<MessageLog> logs, String prefix) {
        return logs.stream()
                .filter(log -> log.getMessageType() != null && log.getMessageType().startsWith(prefix))
                .count();
    }

    private long countFailures(List<MessageLog> logs, String messageType) {
        return logs.stream()
                .filter(log -> messageType.equals(log.getMessageType()))
                .filter(this::isFailure)
                .count();
    }

    private boolean isFailure(MessageLog log) {
        String status = log.getStatus();
        return "FAILED".equalsIgnoreCase(status)
                || "ERROR".equalsIgnoreCase(status)
                || "CIRCUIT_OPEN".equalsIgnoreCase(status);
    }

    private String sanitizeJdbcUrl(String url) {
        if (url == null) return "";
        return url.replaceAll("(?i)(password=)[^;&]+", "$1****");
    }
}
