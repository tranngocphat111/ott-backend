package mediaservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.services.RelationshipService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelationshipEventConsumer {

    private final RelationshipService relationshipService;

    @RabbitListener(queues = "media_service_relationship_updates")
    public void handleRelationshipEvent(Map<String, Object> payload) {
        try {
            log.info("[RelationshipConsumer] Received event: {}", payload);
            
            String type = (String) payload.get("type");
            String requesterId = (String) payload.get("requesterId");
            String receiverId = (String) payload.get("receiverId");
            String status = (String) payload.get("status");

            if (requesterId == null || receiverId == null || status == null) {
                log.warn("[RelationshipConsumer] Missing data in event: {}", payload);
                return;
            }

            // Map status if necessary (e.g., chat-service might use different strings)
            // But here we'll assume they match for now
            relationshipService.syncRelationshipFromEvent(requesterId, receiverId, status, type);
            
        } catch (Exception e) {
            log.error("[RelationshipConsumer] Error processing event: {}", e.getMessage());
        }
    }
}
