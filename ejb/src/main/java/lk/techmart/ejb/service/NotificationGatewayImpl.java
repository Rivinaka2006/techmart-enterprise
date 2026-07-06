package lk.techmart.ejb.service;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import lk.techmart.core.service.MetricsRecorder;
import lk.techmart.core.service.NotificationGateway;
import lk.techmart.ejb.util.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;

@Slf4j
@Stateless
public class NotificationGatewayImpl implements NotificationGateway {

    @EJB
    private MetricsRecorder metricsRecorder;

    private CircuitBreaker emailCircuitBreaker;
    private CircuitBreaker pushCircuitBreaker;
    private final Random random = new Random();

    @PostConstruct
    public void init() {
        
        emailCircuitBreaker = new CircuitBreaker("EmailGateway", 3, 30000);
        pushCircuitBreaker = new CircuitBreaker("PushGateway", 3, 30000);
    }

    @Override
    public boolean sendEmail(String recipient, String subject, String body) {
        if (!emailCircuitBreaker.allowRequest()) {
            log.warn("Email gateway circuit breaker is OPEN, rejecting request");
            metricsRecorder.recordMessageLog("EMAIL_GATEWAY", "CIRCUIT_OPEN", 0);
            return false;
        }

        try {
            boolean success = simulateExternalCall("email");

            if (success) {
                emailCircuitBreaker.recordSuccess();
                metricsRecorder.recordMessageLog("EMAIL_GATEWAY", "SUCCESS", 150);
                log.info("Email sent to {} via external gateway", recipient);
                return true;
            } else {
                emailCircuitBreaker.recordFailure();
                metricsRecorder.recordMessageLog("EMAIL_GATEWAY", "FAILED", 150);
                log.warn("Email gateway returned failure");
                return false;
            }
        } catch (Exception e) {
            emailCircuitBreaker.recordFailure();
            metricsRecorder.recordMessageLog("EMAIL_GATEWAY", "ERROR", 150);
            log.error("Email gateway threw exception", e);
            return false;
        }
    }

    @Override
    public boolean sendPushNotification(String userId, String message) {
        if (!pushCircuitBreaker.allowRequest()) {
            log.warn("Push gateway circuit breaker is OPEN, rejecting request");
            metricsRecorder.recordMessageLog("PUSH_GATEWAY", "CIRCUIT_OPEN", 0);
            return false;
        }

        try {
            boolean success = simulateExternalCall("push");

            if (success) {
                pushCircuitBreaker.recordSuccess();
                metricsRecorder.recordMessageLog("PUSH_GATEWAY", "SUCCESS", 100);
                log.info("Push notification sent to user {} via external gateway", userId);
                return true;
            } else {
                pushCircuitBreaker.recordFailure();
                metricsRecorder.recordMessageLog("PUSH_GATEWAY", "FAILED", 100);
                log.warn("Push gateway returned failure");
                return false;
            }
        } catch (Exception e) {
            pushCircuitBreaker.recordFailure();
            metricsRecorder.recordMessageLog("PUSH_GATEWAY", "ERROR", 100);
            log.error("Push gateway threw exception", e);
            return false;
        }
    }

    



    private boolean simulateExternalCall(String serviceType) {
        
        try {
            Thread.sleep(50 + random.nextInt(100));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        
        return random.nextDouble() < 0.8;
    }
}
