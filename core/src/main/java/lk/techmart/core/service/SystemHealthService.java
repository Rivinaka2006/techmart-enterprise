package lk.techmart.core.service;

import java.time.Instant;
import java.util.Map;

public interface SystemHealthService {
    long getEjbInvocationCount();
    long getMdbProcessingCount();
    long getAsyncTaskCount();
    long getDatabaseQueryCount();
    Instant getStartupTime();
    long getUptimeSeconds();
    Map<String, Long> getAllCounters();


    void incrementEjbInvocation();
    void incrementMdbProcessing();
    void incrementAsyncTask();
    void incrementDatabaseQuery();
}
