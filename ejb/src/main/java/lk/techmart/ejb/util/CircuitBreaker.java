package lk.techmart.ejb.util;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class CircuitBreaker {

    public enum State {
        CLOSED,     
        OPEN,       
        HALF_OPEN   
    }

    private final String serviceName;
    private final int failureThreshold;
    private final long resetTimeoutMs;
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private volatile Instant openedAt;

    public CircuitBreaker(String serviceName, int failureThreshold, long resetTimeoutMs) {
        this.serviceName = serviceName;
        this.failureThreshold = failureThreshold;
        this.resetTimeoutMs = resetTimeoutMs;
    }

    public boolean allowRequest() {
        State currentState = state.get();

        if (currentState == State.OPEN) {
            if (shouldAttemptReset()) {
                log.info("Circuit breaker for {} transitioning to HALF_OPEN", serviceName);
                state.set(State.HALF_OPEN);
                return true;
            }
            return false;
        }

        return true;
    }

    public void recordSuccess() {
        State currentState = state.get();

        if (currentState == State.HALF_OPEN) {
            successCount.incrementAndGet();
            if (successCount.get() >= 2) {
                log.info("Circuit breaker for {} transitioning to CLOSED after successful recovery", serviceName);
                state.set(State.CLOSED);
                failureCount.set(0);
                successCount.set(0);
            }
        } else if (currentState == State.CLOSED) {
            failureCount.set(0); 
        }
    }

    public void recordFailure() {
        State currentState = state.get();
        int failures = failureCount.incrementAndGet();

        if (currentState == State.HALF_OPEN) {
            log.warn("Circuit breaker for {} transitioning back to OPEN after failure in HALF_OPEN", serviceName);
            state.set(State.OPEN);
            openedAt = Instant.now();
            successCount.set(0);
        } else if (currentState == State.CLOSED && failures >= failureThreshold) {
            log.warn("Circuit breaker for {} transitioning to OPEN after {} failures", serviceName, failures);
            state.set(State.OPEN);
            openedAt = Instant.now();
        }
    }

    public State getState() {
        return state.get();
    }

    private boolean shouldAttemptReset() {
        if (openedAt == null) return false;
        long elapsedMs = Instant.now().toEpochMilli() - openedAt.toEpochMilli();
        return elapsedMs >= resetTimeoutMs;
    }
}
