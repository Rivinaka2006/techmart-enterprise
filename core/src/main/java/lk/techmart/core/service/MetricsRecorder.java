package lk.techmart.core.service;

public interface MetricsRecorder {
    void recordPerformanceMetric(String metricName, double value);
    void recordMessageLog(String messageType, String status, int processingTimeMs);
}

