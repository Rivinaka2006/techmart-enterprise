package lk.techmart.ejb.service;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lk.techmart.core.entity.MessageLog;
import lk.techmart.core.entity.PerformanceMetric;
import lk.techmart.core.service.MetricsQueryService;
import lk.techmart.core.service.SystemHealthService;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Stateless
public class MetricsQueryServiceImpl implements MetricsQueryService {
    private static final DateTimeFormatter TIME_LABEL = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter ANALYTICS_LABEL = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    @PersistenceContext(unitName = "TechMartPU")
    private EntityManager em;

    @EJB
    private SystemHealthService healthService;

    @Override
    public List<PerformanceMetric> getRecentPerformanceMetrics(int limit) {
        return em.createQuery(
                        "SELECT p FROM PerformanceMetric p ORDER BY p.recordedAt DESC", PerformanceMetric.class)
                .setMaxResults(safeLimit(limit))
                .getResultList();
    }

    @Override
    public List<MessageLog> getRecentMessageLogs(int limit) {
        return em.createQuery(
                        "SELECT m FROM MessageLog m ORDER BY m.createdAt DESC", MessageLog.class)
                .setMaxResults(safeLimit(limit))
                .getResultList();
    }

    @Override
    public Map<String, Object> getMessagingDashboardData(int limit) {
        List<MessageLog> recentLogs = getRecentMessageLogs(limit);
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        List<MessageLog> todayLogs = em.createQuery(
                        "SELECT m FROM MessageLog m WHERE m.createdAt >= :start ORDER BY m.createdAt DESC",
                        MessageLog.class)
                .setParameter("start", startOfDay)
                .getResultList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", buildSummary(todayLogs));
        data.put("destinations", buildDestinationRows(todayLogs));
        data.put("throughput", buildThroughputRows(todayLogs));
        data.put("delivery", buildDeliverySummary(todayLogs));
        data.put("activity", buildActivityRows(recentLogs));
        return data;
    }

    @Override
    public Map<String, Object> getMonitoringDashboardData(int limit) {
        List<PerformanceMetric> metrics = getRecentPerformanceMetrics(limit);
        Map<String, Long> counters = healthService.getAllCounters();
        Map<String, Object> runtime = buildRuntimeSnapshot(counters);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runtime", runtime);
        data.put("application", buildApplicationMetrics(counters));
        data.put("charts", buildMonitoringCharts(metrics, runtime));
        data.put("nodes", List.of(buildCurrentNode(runtime)));
        return data;
    }

    @Override
    public Map<String, Object> getAnalyticsDashboardData(int hours) {
        int rangeHours = safeRangeHours(hours);
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(rangeHours);

        List<PerformanceMetric> metrics = em.createQuery(
                        "SELECT p FROM PerformanceMetric p WHERE p.recordedAt >= :start ORDER BY p.recordedAt ASC",
                        PerformanceMetric.class)
                .setParameter("start", start)
                .getResultList();
        List<MessageLog> logs = em.createQuery(
                        "SELECT m FROM MessageLog m WHERE m.createdAt >= :start ORDER BY m.createdAt ASC",
                        MessageLog.class)
                .setParameter("start", start)
                .getResultList();

        Map<String, Long> counters = healthService.getAllCounters();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", buildAnalyticsSummary(metrics, logs, counters, rangeHours));
        data.put("charts", buildAnalyticsCharts(metrics, logs, start, end, rangeHours));
        data.put("rangeHours", rangeHours);
        return data;
    }

    private Map<String, Object> buildSummary(List<MessageLog> logs) {
        long processed = logs.size();
        long failed = logs.stream().filter(this::isFailure).count();
        long successful = processed - failed;
        long destinations = logs.stream()
                .map(MessageLog::getMessageType)
                .filter(type -> type != null && !type.isBlank())
                .distinct()
                .count();
        double avgProcessing = logs.stream()
                .map(MessageLog::getProcessingTimeMs)
                .filter(value -> value != null)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("trackedDestinations", destinations);
        summary.put("processedToday", processed);
        summary.put("successfulToday", successful);
        summary.put("failedToday", failed);
        summary.put("avgProcessingMs", Math.round(avgProcessing));
        summary.put("successRate", processed == 0 ? 0 : Math.round((successful * 10000.0) / processed) / 100.0);
        return summary;
    }

    private List<Map<String, Object>> buildDestinationRows(List<MessageLog> logs) {
        Map<String, List<MessageLog>> grouped = logs.stream()
                .collect(Collectors.groupingBy(
                        log -> log.getMessageType() == null ? "UNKNOWN" : log.getMessageType(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, List<MessageLog>> entry : grouped.entrySet()) {
            String type = entry.getKey();
            List<MessageLog> typeLogs = entry.getValue();
            long failed = typeLogs.stream().filter(this::isFailure).count();
            double avgMs = typeLogs.stream()
                    .map(MessageLog::getProcessingTimeMs)
                    .filter(value -> value != null)
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("messageType", type);
            row.put("destinationName", destinationName(type));
            row.put("destinationType", destinationType(type));
            row.put("processed", typeLogs.size());
            row.put("failed", failed);
            row.put("avgProcessingMs", Math.round(avgMs));
            row.put("status", failed == 0 ? "HEALTHY" : "ATTENTION");
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> buildThroughputRows(List<MessageLog> logs) {
        return buildDestinationRows(logs).stream()
                .map(row -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("label", row.get("messageType"));
                    item.put("value", row.get("processed"));
                    return item;
                })
                .toList();
    }

    private Map<String, Object> buildDeliverySummary(List<MessageLog> logs) {
        long topicMessages = logs.stream()
                .filter(log -> log.getMessageType() != null && log.getMessageType().startsWith("TOPIC_"))
                .count();
        long gatewayCalls = logs.stream()
                .filter(log -> log.getMessageType() != null && log.getMessageType().endsWith("_GATEWAY"))
                .count();
        long failed = logs.stream().filter(this::isFailure).count();
        long total = logs.size();
        long successful = total - failed;

        Map<String, Object> delivery = new LinkedHashMap<>();
        delivery.put("topicMessages", topicMessages);
        delivery.put("gatewayCalls", gatewayCalls);
        delivery.put("total", total);
        delivery.put("successful", successful);
        delivery.put("successRate", total == 0 ? 0 : Math.round((successful * 10000.0) / total) / 100.0);
        delivery.put("failed", failed);
        return delivery;
    }

    private List<Map<String, Object>> buildActivityRows(List<MessageLog> logs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MessageLog log : logs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", log.getId());
            row.put("messageType", log.getMessageType());
            row.put("destinationName", destinationName(log.getMessageType()));
            row.put("status", log.getStatus());
            row.put("processingTimeMs", log.getProcessingTimeMs());
            row.put("createdAt", log.getCreatedAt() == null ? null : log.getCreatedAt().toString());
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> buildRuntimeSnapshot(Map<String, Long> counters) {
        Runtime runtime = Runtime.getRuntime();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        long runtimeMaxBytes = runtime.maxMemory();
        long runtimeUsedBytes = runtime.totalMemory() - runtime.freeMemory();
        long heapMaxBytes = heap.getMax();
        long heapUsedBytes = heap.getUsed();
        double cpuPercent = currentCpuPercent();
        double memoryPercent = percent(runtimeUsedBytes, runtimeMaxBytes);
        double heapPercent = percent(heapUsedBytes, heapMaxBytes);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("nodeName", currentNodeName());
        snapshot.put("cpuPercent", round(cpuPercent));
        snapshot.put("availableProcessors", ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors());
        snapshot.put("memoryPercent", round(memoryPercent));
        snapshot.put("memoryUsedMb", bytesToMb(runtimeUsedBytes));
        snapshot.put("memoryMaxMb", bytesToMb(runtimeMaxBytes));
        snapshot.put("heapPercent", round(heapPercent));
        snapshot.put("heapUsedMb", bytesToMb(heapUsedBytes));
        snapshot.put("heapMaxMb", bytesToMb(heapMaxBytes));
        snapshot.put("threadCount", threadBean.getThreadCount());
        snapshot.put("daemonThreadCount", threadBean.getDaemonThreadCount());
        snapshot.put("peakThreadCount", threadBean.getPeakThreadCount());
        snapshot.put("uptimeSeconds", counters.getOrDefault("uptimeSeconds", 0L));
        snapshot.put("status", statusFor(cpuPercent, memoryPercent, heapPercent));
        return snapshot;
    }

    private Map<String, Object> buildApplicationMetrics(Map<String, Long> counters) {
        Map<String, Object> application = new LinkedHashMap<>();
        application.put("ejbInvocations", counters.getOrDefault("ejbInvocations", 0L));
        application.put("mdbProcessing", counters.getOrDefault("mdbProcessing", 0L));
        application.put("asyncTasks", counters.getOrDefault("asyncTasks", 0L));
        application.put("dbQueries", counters.getOrDefault("dbQueries", 0L));
        return application;
    }

    private Map<String, Object> buildMonitoringCharts(List<PerformanceMetric> metrics, Map<String, Object> runtime) {
        Map<String, Object> charts = new LinkedHashMap<>();
        charts.put("cpuMemory", Map.of(
                "labels", labelsFor(metrics, "system.cpu_percent", runtime),
                "cpu", valuesFor(metrics, "system.cpu_percent", runtime.get("cpuPercent")),
                "memory", valuesFor(metrics, "system.memory_percent", runtime.get("memoryPercent"))
        ));
        charts.put("heapThread", Map.of(
                "labels", labelsFor(metrics, "system.heap_percent", runtime),
                "heap", valuesFor(metrics, "system.heap_percent", runtime.get("heapPercent")),
                "threads", valuesFor(metrics, "system.thread_count", runtime.get("threadCount"))
        ));
        charts.put("counters", Map.of(
                "labels", labelsFor(metrics, "system.ejb_invocations", runtime),
                "ejbInvocations", valuesFor(metrics, "system.ejb_invocations", 0),
                "mdbProcessing", valuesFor(metrics, "system.mdb_processing", 0),
                "asyncTasks", valuesFor(metrics, "system.async_tasks", 0),
                "dbQueries", valuesFor(metrics, "system.db_queries", 0)
        ));
        return charts;
    }

    private Map<String, Object> buildCurrentNode(Map<String, Object> runtime) {
        String status = String.valueOf(runtime.get("status"));
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", runtime.get("nodeName"));
        node.put("cpuPercent", runtime.get("cpuPercent"));
        node.put("memoryPercent", runtime.get("memoryPercent"));
        node.put("threads", runtime.get("threadCount"));
        node.put("heapPercent", runtime.get("heapPercent"));
        node.put("status", status);
        return node;
    }

    private List<String> labelsFor(List<PerformanceMetric> metrics, String metricName, Map<String, Object> runtime) {
        List<String> labels = metricsFor(metrics, metricName).stream()
                .map(metric -> formatTime(metric.getRecordedAt()))
                .toList();
        if (!labels.isEmpty()) return labels;
        return List.of(formatUptimeLabel(runtime.get("uptimeSeconds")));
    }

    private List<BigDecimal> valuesFor(List<PerformanceMetric> metrics, String metricName, Object fallback) {
        List<BigDecimal> values = metricsFor(metrics, metricName).stream()
                .map(PerformanceMetric::getMetricValue)
                .toList();
        if (!values.isEmpty()) return values;
        return List.of(BigDecimal.valueOf(toDouble(fallback)));
    }

    private List<PerformanceMetric> metricsFor(List<PerformanceMetric> metrics, String metricName) {
        return metrics.stream()
                .filter(metric -> metricName.equals(metric.getMetricName()))
                .sorted(Comparator.comparing(
                        PerformanceMetric::getRecordedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .toList();
    }

    private Map<String, Object> buildAnalyticsSummary(
            List<PerformanceMetric> metrics,
            List<MessageLog> logs,
            Map<String, Long> counters,
            int rangeHours) {
        List<BigDecimal> responseValues = responseMetrics(metrics).stream()
                .map(PerformanceMetric::getMetricValue)
                .filter(value -> value != null)
                .toList();
        long failedMessages = logs.stream().filter(this::isFailure).count();
        long totalMessages = logs.size();
        double avgResponse = average(responseValues);
        double peakResponse = responseValues.stream()
                .mapToDouble(BigDecimal::doubleValue)
                .max()
                .orElse(0);
        double rangeMinutes = Math.max(1, rangeHours * 60.0);
        double throughputPerMinute = responseValues.size() / rangeMinutes;
        long uptimeSeconds = counters.getOrDefault("uptimeSeconds", 0L);
        double requestsPerSecond = uptimeSeconds <= 0
                ? 0
                : counters.getOrDefault("ejbInvocations", 0L) / (double) uptimeSeconds;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("avgResponseMs", Math.round(avgResponse));
        summary.put("peakResponseMs", Math.round(peakResponse));
        summary.put("throughputPerMinute", round(throughputPerMinute));
        summary.put("errorRate", totalMessages == 0 ? 0 : round((failedMessages * 100.0) / totalMessages));
        summary.put("requestsPerSecond", round(requestsPerSecond));
        summary.put("operationSamples", responseValues.size());
        summary.put("totalMessages", totalMessages);
        summary.put("failedMessages", failedMessages);
        return summary;
    }

    private Map<String, Object> buildAnalyticsCharts(
            List<PerformanceMetric> metrics,
            List<MessageLog> logs,
            LocalDateTime start,
            LocalDateTime end,
            int rangeHours) {
        int bucketMinutes = analyticsBucketMinutes(rangeHours);
        List<LocalDateTime> buckets = buildBuckets(start, end, bucketMinutes);
        List<String> labels = buckets.stream()
                .map(ANALYTICS_LABEL::format)
                .toList();

        Map<String, Object> charts = new LinkedHashMap<>();
        charts.put("throughputTrend", Map.of(
                "labels", labels,
                "values", buildThroughputBuckets(responseMetrics(metrics), buckets, bucketMinutes)
        ));
        charts.put("responseTrend", buildResponseTrend(responseMetrics(metrics), labels, buckets, bucketMinutes));
        charts.put("queueTrend", Map.of(
                "labels", labels,
                "values", buildMessageBuckets(logs, buckets, bucketMinutes)
        ));
        charts.put("resourceTrend", buildResourceTrend(metrics, labels, buckets, bucketMinutes));
        return charts;
    }

    private Map<String, Object> buildResponseTrend(
            List<PerformanceMetric> metrics,
            List<String> labels,
            List<LocalDateTime> buckets,
            int bucketMinutes) {
        List<List<Double>> valuesByBucket = emptyDoubleBuckets(buckets.size());
        for (PerformanceMetric metric : metrics) {
            int index = bucketIndex(metric.getRecordedAt(), buckets, bucketMinutes);
            if (index >= 0) valuesByBucket.get(index).add(toDouble(metric.getMetricValue()));
        }

        List<Double> p50 = new ArrayList<>();
        List<Double> p95 = new ArrayList<>();
        for (List<Double> values : valuesByBucket) {
            p50.add(percentile(values, 50));
            p95.add(percentile(values, 95));
        }

        return Map.of("labels", labels, "p50", p50, "p95", p95);
    }

    private Map<String, Object> buildResourceTrend(
            List<PerformanceMetric> metrics,
            List<String> labels,
            List<LocalDateTime> buckets,
            int bucketMinutes) {
        return Map.of(
                "labels", labels,
                "cpu", buildAverageMetricBuckets(metrics, "system.cpu_percent", buckets, bucketMinutes),
                "memory", buildAverageMetricBuckets(metrics, "system.memory_percent", buckets, bucketMinutes)
        );
    }

    private List<Double> buildThroughputBuckets(List<PerformanceMetric> metrics, List<LocalDateTime> buckets, int bucketMinutes) {
        List<Double> values = zeroBuckets(buckets.size());
        double minutes = Math.max(1, bucketMinutes);
        for (PerformanceMetric metric : metrics) {
            int index = bucketIndex(metric.getRecordedAt(), buckets, bucketMinutes);
            if (index >= 0) values.set(index, values.get(index) + (1.0 / minutes));
        }
        return values.stream().map(this::round).toList();
    }

    private List<Long> buildMessageBuckets(List<MessageLog> logs, List<LocalDateTime> buckets, int bucketMinutes) {
        List<Long> values = new ArrayList<>(Collections.nCopies(buckets.size(), 0L));
        for (MessageLog log : logs) {
            int index = bucketIndex(log.getCreatedAt(), buckets, bucketMinutes);
            if (index >= 0) values.set(index, values.get(index) + 1);
        }
        return values;
    }

    private List<Double> buildAverageMetricBuckets(
            List<PerformanceMetric> metrics,
            String metricName,
            List<LocalDateTime> buckets,
            int bucketMinutes) {
        List<List<Double>> valuesByBucket = emptyDoubleBuckets(buckets.size());
        for (PerformanceMetric metric : metrics) {
            if (!metricName.equals(metric.getMetricName())) continue;
            int index = bucketIndex(metric.getRecordedAt(), buckets, bucketMinutes);
            if (index >= 0) valuesByBucket.get(index).add(toDouble(metric.getMetricValue()));
        }
        return valuesByBucket.stream()
                .map(values -> round(average(values)))
                .toList();
    }

    private List<PerformanceMetric> responseMetrics(List<PerformanceMetric> metrics) {
        return metrics.stream()
                .filter(metric -> metric.getMetricName() != null && !metric.getMetricName().startsWith("system."))
                .filter(metric -> metric.getMetricValue() != null)
                .toList();
    }

    private List<LocalDateTime> buildBuckets(LocalDateTime start, LocalDateTime end, int bucketMinutes) {
        long totalMinutes = Math.max(1, Duration.between(start, end).toMinutes());
        int bucketCount = Math.max(1, (int) Math.ceil(totalMinutes / (double) bucketMinutes));
        List<LocalDateTime> buckets = new ArrayList<>();
        for (int i = 0; i < bucketCount; i++) {
            buckets.add(start.plusMinutes((long) i * bucketMinutes));
        }
        return buckets;
    }

    private int bucketIndex(LocalDateTime dateTime, List<LocalDateTime> buckets, int bucketMinutes) {
        if (dateTime == null || buckets.isEmpty()) return -1;
        long minutes = Duration.between(buckets.get(0), dateTime).toMinutes();
        int index = (int) Math.floor(minutes / (double) bucketMinutes);
        if (index < 0) return -1;
        return Math.min(index, buckets.size() - 1);
    }

    private List<Double> zeroBuckets(int size) {
        return new ArrayList<>(Collections.nCopies(size, 0.0));
    }

    private List<List<Double>> emptyDoubleBuckets(int size) {
        List<List<Double>> buckets = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            buckets.add(new ArrayList<>());
        }
        return buckets;
    }

    private double percentile(List<Double> values, int percentile) {
        if (values.isEmpty()) return 0;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return round(sorted.get(index));
    }

    private double average(List<? extends Number> values) {
        if (values.isEmpty()) return 0;
        return values.stream()
                .mapToDouble(value -> value.doubleValue())
                .average()
                .orElse(0);
    }

    private boolean isFailure(MessageLog log) {
        String status = log.getStatus();
        return "FAILED".equalsIgnoreCase(status)
                || "ERROR".equalsIgnoreCase(status)
                || "CIRCUIT_OPEN".equalsIgnoreCase(status);
    }

    private String destinationType(String messageType) {
        if ("ORDER_SUBMIT".equals(messageType)) return "Queue";
        if (messageType != null && messageType.startsWith("TOPIC_")) return "Topic";
        if (messageType != null && messageType.endsWith("_GATEWAY")) return "Gateway";
        return "Message";
    }

    private String destinationName(String messageType) {
        if ("ORDER_SUBMIT".equals(messageType)) return "jms/queue/OrderQueue";
        if ("TOPIC_EMAIL".equals(messageType)) return "jms/topic/InventoryUpdatesTopic -> Email";
        if ("TOPIC_PUSH".equals(messageType)) return "jms/topic/InventoryUpdatesTopic -> Push";
        if ("EMAIL_GATEWAY".equals(messageType)) return "Email notification gateway";
        if ("PUSH_GATEWAY".equals(messageType)) return "Push notification gateway";
        return messageType == null ? "Unknown message stream" : messageType;
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit, 500));
    }

    private int safeRangeHours(int hours) {
        if (hours <= 24) return 24;
        if (hours <= 168) return 168;
        return 720;
    }

    private int analyticsBucketMinutes(int rangeHours) {
        if (rangeHours <= 24) return 60;
        if (rangeHours <= 168) return 720;
        return 1440;
    }

    private double currentCpuPercent() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean osBean) {
            double load = osBean.getCpuLoad();
            if (load < 0) load = osBean.getProcessCpuLoad();
            if (load >= 0) return load * 100.0;
        }
        double systemLoad = bean.getSystemLoadAverage();
        int processors = Math.max(1, bean.getAvailableProcessors());
        if (systemLoad >= 0) return Math.min(100.0, (systemLoad / processors) * 100.0);
        return 0;
    }

    private double percent(long used, long max) {
        if (used < 0 || max <= 0) return 0;
        return (used * 100.0) / max;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private long bytesToMb(long bytes) {
        return bytes <= 0 ? 0 : Math.round(bytes / 1024.0 / 1024.0);
    }

    private String statusFor(double cpuPercent, double memoryPercent, double heapPercent) {
        double max = Math.max(cpuPercent, Math.max(memoryPercent, heapPercent));
        if (max >= 90) return "CRITICAL";
        if (max >= 75) return "HIGH_LOAD";
        return "HEALTHY";
    }

    private String currentNodeName() {
        String configuredName = System.getProperty("jboss.node.name");
        if (configuredName != null && !configuredName.isBlank()) return configuredName;
        String computerName = System.getenv("COMPUTERNAME");
        if (computerName != null && !computerName.isBlank()) return computerName;
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "local-jvm";
        }
    }

    private String formatTime(LocalDateTime dateTime) {
        return dateTime == null ? "--:--:--" : TIME_LABEL.format(dateTime);
    }

    private String formatUptimeLabel(Object uptimeSeconds) {
        long seconds = Math.max(0, Math.round(toDouble(uptimeSeconds)));
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return minutes + "m " + remainingSeconds + "s";
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return 0;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
