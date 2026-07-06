package lk.techmart.ejb.service;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import lk.techmart.core.dto.NotificationEvent;
import lk.techmart.core.service.NotificationDispatcher;
import lk.techmart.core.service.NotificationGateway;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Stateless
public class NotificationDispatcherImpl implements NotificationDispatcher {

    @EJB
    private NotificationGateway gateway;

    @Override
    public void dispatchEmail(NotificationEvent event) {
        log.info("Dispatching email notification for event: {}", event.getEventType());

        String subject = buildEmailSubject(event);
        String body = buildEmailBody(event);

        
        String recipient = "admin@techmart.lk";

        boolean success = gateway.sendEmail(recipient, subject, body);

        if (success) {
            log.info("Email notification sent successfully for {}", event.getEventType());
        } else {
            log.warn("Email notification failed for {}", event.getEventType());
        }
    }

    @Override
    public void dispatchPush(NotificationEvent event) {
        log.info("Dispatching push notification for event: {}", event.getEventType());

        String message = buildPushMessage(event);

        
        String userId = "admin";

        boolean success = gateway.sendPushNotification(userId, message);

        if (success) {
            log.info("Push notification sent successfully for {}", event.getEventType());
        } else {
            log.warn("Push notification failed for {}", event.getEventType());
        }
    }

    private String buildEmailSubject(NotificationEvent event) {
        return switch (event.getEventType()) {
            case "LOW_STOCK" -> "Low Stock Alert - Product " + event.getEntityId();
            case "ORDER_COMPLETED" -> "Order Completed - #" + event.getEntityId();
            default -> "TechMart Notification - " + event.getEventType();
        };
    }

    private String buildEmailBody(NotificationEvent event) {
        return String.format(
            "Event Type: %s\nEntity ID: %d\nMessage: %s\nTimestamp: %s\nMetadata: %s",
            event.getEventType(),
            event.getEntityId(),
            event.getMessage(),
            event.getTimestamp(),
            event.getMetadata()
        );
    }

    private String buildPushMessage(NotificationEvent event) {
        return event.getMessage();
    }
}
