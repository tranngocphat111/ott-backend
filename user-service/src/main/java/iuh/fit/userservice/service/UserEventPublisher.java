package iuh.fit.userservice.service;

import iuh.fit.userservice.config.RabbitMQConfig;
import iuh.fit.userservice.dto.event.UserCreatedEvent;
import iuh.fit.userservice.dto.event.UserStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQConfig rabbitMQConfig;

    public void publishUserCreated(UserCreatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    rabbitMQConfig.userEventsExchange,
                    rabbitMQConfig.userCreatedRoutingKey,
                    event
            );
            log.info("Published user.created event for userId={}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to publish user.created event for userId={}: {}", 
                    event.getUserId(), e.getMessage());
        }
    }

    public void publishUserUpdated(iuh.fit.userservice.dto.event.UserUpdatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    rabbitMQConfig.userEventsExchange,
                    rabbitMQConfig.userUpdatedRoutingKey,
                    event
            );
            log.info("Published user.updated event for userId={}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to publish user.updated event for userId={}: {}", 
                    event.getUserId(), e.getMessage());
        }
    }

    public void publishUserLogout(iuh.fit.userservice.dto.event.UserLogoutEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    rabbitMQConfig.userEventsExchange,
                    rabbitMQConfig.userLogoutRoutingKey,
                    event
            );
            log.info("Published user.logout event for userId={}, action={}", event.getUserId(), event.getAction());
        } catch (Exception e) {
            log.error("Failed to publish user.logout event for userId={}: {}", 
                    event.getUserId(), e.getMessage());
        }
    }

    public void publishUserStatusChanged(UserStatusChangedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    rabbitMQConfig.userEventsExchange,
                    rabbitMQConfig.userStatusChangedRoutingKey,
                    event
            );
            log.info("Published user.status.changed event for userId={}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to publish user.status.changed event for userId={}: {}",
                    event.getUserId(), e.getMessage());
        }
    }
}
