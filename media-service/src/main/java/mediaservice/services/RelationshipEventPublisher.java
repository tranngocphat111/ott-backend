package mediaservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.models.Relationship;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelationshipEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private static final String EXCHANGE_NAME = "relationship.events";

    public void publish(String type, Relationship relationship, String actorId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", type);
            payload.put("relationshipId", relationship.getId());
            payload.put("requesterId", relationship.getRequester() != null ? relationship.getRequester().getId() : null);
            payload.put("receiverId", relationship.getReceiver() != null ? relationship.getReceiver().getId() : null);
            payload.put("status", relationship.getStatus() != null ? relationship.getStatus().name() : null);
            payload.put("actorId", actorId);
            payload.put("requesterDisplayName", relationship.getRequester() != null ? relationship.getRequester().getDisplayName() : null);
            payload.put("receiverDisplayName", relationship.getReceiver() != null ? relationship.getReceiver().getDisplayName() : null);
            payload.put("blockedById", relationship.getBlockedBy() != null ? relationship.getBlockedBy().getId() : null);
            payload.put("blockedByDisplayName", relationship.getBlockedBy() != null ? relationship.getBlockedBy().getDisplayName() : null);
            payload.put("timestamp", Instant.now().toString());

            String routingKey = "relationship." + type.toLowerCase();
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, routingKey, payload);
            log.info("[RelationshipPublisher] Published {} event to {}", type, routingKey);
        } catch (Exception e) {
            log.error("[RelationshipPublisher] Failed to publish event: {}", e.getMessage());
        }
    }
}
