package lk.techmart.ejb.monitor;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lk.techmart.core.entity.PerformanceMetric;
import lk.techmart.core.service.SystemHealthService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Singleton
@Startup
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
public class SystemHealthMonitorBean implements SystemHealthService {

    @PersistenceContext(unitName = "TechMartPU")
    private EntityManager em;

    private final AtomicLong ejbInvocations = new AtomicLong(0);
    private final AtomicLong mdbProcessing = new AtomicLong(0);
    private final AtomicLong asyncTasks = new AtomicLong(0);
    private final AtomicLong dbQueries = new AtomicLong(0);
    private Instant startupTime;

    @PostConstruct
    public void init() {
        startupTime = Instant.now();
        log.info("SystemHealthMonitorBean initialized at {}", startupTime);
    }

    @Lock(LockType.READ)
    @Override
    public long getEjbInvocationCount() {
        return ejbInvocations.get();
    }

    @Lock(LockType.READ)
    @Override
    public long getMdbProcessingCount() {
        return mdbProcessing.get();
    }

    @Lock(LockType.READ)
    @Override
    public long getAsyncTaskCount() {
        return asyncTasks.get();
    }

    @Lock(LockType.READ)
    @Override
    public long getDatabaseQueryCount() {
        return dbQueries.get();
    }

    @Lock(LockType.READ)
    @Override
    public Instant getStartupTime() {
        return startupTime;
    }

    @Lock(LockType.READ)
    @Override
    public long getUptimeSeconds() {
        return Instant.now().getEpochSecond() - startupTime.getEpochSecond();
    }

    @Lock(LockType.READ)
    @Override
    public Map<String, Long> getAllCounters() {
        Map<String, Long> counters = new HashMap<>();
        counters.put("ejbInvocations", ejbInvocations.get());
        counters.put("mdbProcessing", mdbProcessing.get());
        counters.put("asyncTasks", asyncTasks.get());
        counters.put("dbQueries", dbQueries.get());
        counters.put("uptimeSeconds", getUptimeSeconds());
        return counters;
    }

    @Lock(LockType.WRITE)
    @Override
    public void incrementEjbInvocation() {
        ejbInvocations.incrementAndGet();
    }

    @Lock(LockType.WRITE)
    @Override
    public void incrementMdbProcessing() {
        mdbProcessing.incrementAndGet();
    }

    @Lock(LockType.WRITE)
    @Override
    public void incrementAsyncTask() {
        asyncTasks.incrementAndGet();
    }

    @Lock(LockType.WRITE)
    @Override
    public void incrementDatabaseQuery() {
        dbQueries.incrementAndGet();
    }


    @Schedule(second = "*/30", minute = "*", hour = "*", persistent = false)
    @Lock(LockType.READ)
    public void snapshotMetrics() {
        try {
            persistMetric("system.ejb_invocations", ejbInvocations.get());
            persistMetric("system.mdb_processing", mdbProcessing.get());
            persistMetric("system.async_tasks", asyncTasks.get());
            persistMetric("system.db_queries", dbQueries.get());
            persistMetric("system.uptime_seconds", getUptimeSeconds());
            persistMetric("system.cpu_percent", currentCpuPercent());
            persistMetric("system.memory_percent", currentJvmMemoryPercent());
            persistMetric("system.heap_percent", currentHeapPercent());
            persistMetric("system.thread_count", currentThreadCount());

            log.debug("Health metrics snapshot persisted to performance_metrics");
        } catch (Exception e) {
            log.error("Failed to persist health metrics snapshot", e);
        }
    }

    private void persistMetric(String metricName, double value) {
        PerformanceMetric metric = new PerformanceMetric();
        metric.setMetricName(metricName);
        metric.setMetricValue(BigDecimal.valueOf(value));
        metric.setRecordedAt(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));
        em.persist(metric);
    }

    private double currentCpuPercent() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean osBean) {
            double load = osBean.getCpuLoad();
            if (load < 0) load = osBean.getProcessCpuLoad();
            if (load >= 0) return Math.round(load * 10000.0) / 100.0;
        }
        double systemLoad = bean.getSystemLoadAverage();
        int processors = Math.max(1, bean.getAvailableProcessors());
        if (systemLoad >= 0) return Math.min(100.0, Math.round((systemLoad / processors) * 10000.0) / 100.0);
        return 0;
    }

    private double currentJvmMemoryPercent() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        if (max <= 0) return 0;
        long used = runtime.totalMemory() - runtime.freeMemory();
        return Math.round((used * 10000.0) / max) / 100.0;
    }

    private double currentHeapPercent() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        long max = heap.getMax();
        if (max <= 0) return 0;
        return Math.round((heap.getUsed() * 10000.0) / max) / 100.0;
    }

    private int currentThreadCount() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        return threadBean.getThreadCount();
    }
}
