package lk.techmart.ejb.service;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lk.techmart.core.entity.MessageLog;
import lk.techmart.core.entity.PerformanceMetric;
import lk.techmart.core.service.MetricsRecorder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Stateless
public class MetricsRecorderImpl implements MetricsRecorder {

    @PersistenceContext(unitName = "TechMartPU")
    private EntityManager em;

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW) 
    public void recordPerformanceMetric(String metricName, double value) {
        PerformanceMetric metric = new PerformanceMetric();
        metric.setMetricName(metricName);
        metric.setMetricValue(BigDecimal.valueOf(value));
        metric.setRecordedAt(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));
        em.persist(metric);
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void recordMessageLog(String messageType, String status, int processingTimeMs) {
        MessageLog log = new MessageLog();
        log.setMessageType(messageType);
        log.setStatus(status);
        log.setProcessingTimeMs(processingTimeMs);
        log.setCreatedAt(LocalDateTime.now());
        em.persist(log);
    }
}