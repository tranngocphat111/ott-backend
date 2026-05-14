package mediaservice.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange.notification:notification.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing-key.inapp:notification.inapp}")
    private String routingKey;

    public void publishNotification(String recipientId, String senderId, String type, String content, String referenceId) {
        if (recipientId == null || recipientId.equals(senderId)) {
            return; // Don't notify yourself
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientId", recipientId);
        payload.put("senderId", senderId);
        payload.put("type", type);
        payload.put("content", content);
        payload.put("referenceId", referenceId);

        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, payload);
            log.info("Published {} notification to user {}", type, recipientId);
        } catch (Exception e) {
            log.error("Failed to publish notification", e);
        }
    }
}
