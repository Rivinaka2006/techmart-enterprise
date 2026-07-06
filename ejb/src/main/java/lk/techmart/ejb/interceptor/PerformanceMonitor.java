package lk.techmart.ejb.interceptor;

import jakarta.ejb.EJB;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.InvocationContext;
import lk.techmart.core.service.MetricsRecorder;
import lk.techmart.core.service.SystemHealthService;

public class PerformanceMonitor {

    @EJB
    private MetricsRecorder metricsRecorder;

    @EJB
    private SystemHealthService healthService;

    @AroundInvoke
    public Object aroundInvoke(InvocationContext ctx) throws Exception {
        long start = System.nanoTime();
        try {
            healthService.incrementEjbInvocation();
            return ctx.proceed();
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            String label = ctx.getTarget().getClass().getSimpleName() + "." + ctx.getMethod().getName();
            metricsRecorder.recordPerformanceMetric(label, elapsedMs);
        }
    }
}
