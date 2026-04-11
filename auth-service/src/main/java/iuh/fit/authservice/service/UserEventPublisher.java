package iuh.fit.authservice.service;

import iuh.fit.authservice.config.RabbitMQConfig;
import iuh.fit.authservice.dto.event.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishUserCreated(UserCreatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.USER_CREATED_EXCHANGE,
                    RabbitMQConfig.ROUTING_KEY_USER_CREATED,
                    event
            );
            log.info("Published user.created event for userId={}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to publish user.created event for userId={}", event.getUserId(), e);
        }
    }
}
