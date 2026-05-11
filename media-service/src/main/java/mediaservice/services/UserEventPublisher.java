package mediaservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.dtos.events.UserUpdatedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${user.events.exchange:user.events}")
    private String userEventsExchange;

    @Value("${user.updated.routing-key:user.updated}")
    private String userUpdatedRoutingKey;

    public void publishUserUpdated(UserUpdatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(userEventsExchange, userUpdatedRoutingKey, event);
            log.info("[UserPublisher] Published user.updated event for userId: {}", event.getUserId());
        } catch (Exception e) {
            log.error("[UserPublisher] Failed to publish user.updated event: {}", e.getMessage());
        }
    }
}
