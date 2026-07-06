package lk.techmart.core.service;

import lk.techmart.core.entity.MessageLog;
import lk.techmart.core.entity.PerformanceMetric;

import java.util.List;
import java.util.Map;

public interface MetricsQueryService {
    List<PerformanceMetric> getRecentPerformanceMetrics(int limit);
    List<MessageLog> getRecentMessageLogs(int limit);
    Map<String, Object> getMessagingDashboardData(int limit);
    Map<String, Object> getMonitoringDashboardData(int limit);
    Map<String, Object> getAnalyticsDashboardData(int hours);
}
