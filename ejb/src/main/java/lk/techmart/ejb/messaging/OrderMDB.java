package lk.techmart.ejb.messaging;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.EJBException;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import lk.techmart.core.dto.OrderPayload;
import lk.techmart.core.service.MetricsRecorder;
import lk.techmart.core.service.OrderProcessor;
import lk.techmart.core.service.SystemHealthService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "jms/queue/OrderQueue"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")
})
public class OrderMDB implements MessageListener {

    private static final Jsonb JSONB = JsonbBuilder.create();

    @Inject
    private OrderProcessor orderProcessor;

    @Inject
    private MetricsRecorder metricsRecorder;

    @Inject
    private SystemHealthService healthService;

    @Override
    public void onMessage(Message message) {
        long start = System.nanoTime();
        String status = "SUCCESS";
        try {
            String json = ((TextMessage) message).getText();
            OrderPayload payload = JSONB.fromJson(json, OrderPayload.class);
            orderProcessor.processOrder(payload);
            healthService.incrementMdbProcessing();
        } catch (JMSException | RuntimeException e) {
            status = "FAILED";
            log.error("Order message processing failed", e);


            throw new EJBException("Failed to process order message", e);
        } finally {
            int elapsedMs = (int) ((System.nanoTime() - start) / 1_000_000);
            metricsRecorder.recordMessageLog("ORDER_SUBMIT", status, elapsedMs);
        }
    }
}