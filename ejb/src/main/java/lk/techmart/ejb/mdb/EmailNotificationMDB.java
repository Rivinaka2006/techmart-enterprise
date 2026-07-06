package lk.techmart.ejb.mdb;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.EJB;
import jakarta.ejb.MessageDriven;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import lk.techmart.core.dto.NotificationEvent;
import lk.techmart.core.service.MetricsRecorder;
import lk.techmart.core.service.NotificationDispatcher;
import lk.techmart.core.service.SystemHealthService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@MessageDriven(
    name = "EmailNotificationMDB",
    activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "jms/topic/InventoryUpdatesTopic"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
        @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
        @ActivationConfigProperty(propertyName = "clientId", propertyValue = "EmailNotificationClient"),
        @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "EmailSubscription")
    }
)
public class EmailNotificationMDB implements MessageListener {

    @EJB
    private NotificationDispatcher dispatcher;

    @EJB
    private MetricsRecorder metricsRecorder;

    @EJB
    private SystemHealthService healthService;

    @Override
    public void onMessage(Message message) {
        long start = System.currentTimeMillis();
        try {
            NotificationEvent event = message.getBody(NotificationEvent.class);
            log.info("EmailNotificationMDB received event: type={}, entityId={}", event.getEventType(), event.getEntityId());

            dispatcher.dispatchEmail(event);

            int duration = (int) (System.currentTimeMillis() - start);
            metricsRecorder.recordMessageLog("TOPIC_EMAIL", "SUCCESS", duration);
            healthService.incrementMdbProcessing();

        } catch (JMSException e) {
            log.error("Error processing email notification", e);
            int duration = (int) (System.currentTimeMillis() - start);
            metricsRecorder.recordMessageLog("TOPIC_EMAIL", "FAILED", duration);
        }
    }
}
