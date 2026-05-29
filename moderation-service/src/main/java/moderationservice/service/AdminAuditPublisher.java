package moderationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import moderationservice.contracts.AdminAuditEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuditPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${moderation.rabbitmq.exchange:moderation.events}")
    private String exchangeName;

    @Value("${moderation.rabbitmq.routing-key.admin-audit:moderation.admin.audit}")
    private String adminAuditRoutingKey;

    public void publish(AdminAuditEvent event) {
        try {
            rabbitTemplate.convertAndSend(exchangeName, adminAuditRoutingKey, event);
            log.info("Published admin audit event: eventId={}, actionType={}",
                    event.getEventId(), event.getActionType());
        } catch (RuntimeException ex) {
            log.error("Failed to publish admin audit event: eventId={}, actionType={}",
                    event.getEventId(), event.getActionType(), ex);
        }
    }
}
