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
    name = "PushNotificationMDB",
    activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "jms/topic/InventoryUpdatesTopic"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
        @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "NonDurable"),
        @ActivationConfigProperty(propertyName = "clientId", propertyValue = "PushNotificationClient"),
        @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = "PushSubscription")
    }
)
public class PushNotificationMDB implements MessageListener {

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
            log.info("PushNotificationMDB received event: type={}, entityId={}", event.getEventType(), event.getEntityId());

            dispatcher.dispatchPush(event);

            int duration = (int) (System.currentTimeMillis() - start);
            metricsRecorder.recordMessageLog("TOPIC_PUSH", "SUCCESS", duration);
            healthService.incrementMdbProcessing();

        } catch (JMSException e) {
            log.error("Error processing push notification", e);
            int duration = (int) (System.currentTimeMillis() - start);
            metricsRecorder.recordMessageLog("TOPIC_PUSH", "FAILED", duration);
        }
    }
}
