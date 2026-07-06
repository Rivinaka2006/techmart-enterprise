package lk.techmart.core.service;

public interface NotificationGateway {
    boolean sendEmail(String recipient, String subject, String body);
    boolean sendPushNotification(String userId, String message);
}
