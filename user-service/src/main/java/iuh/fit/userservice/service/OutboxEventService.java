package iuh.fit.userservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.userservice.config.RabbitMQConfig;
import iuh.fit.userservice.dto.event.UserStatusChangedEvent;
import iuh.fit.userservice.entity.OutboxEvent;
import iuh.fit.userservice.entity.enums.OutboxStatus;
import iuh.fit.userservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventService {

    private static final String USER_STATUS_CHANGED_TYPE = "user.status.changed";

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQConfig rabbitMQConfig;
    private final ObjectMapper objectMapper;

    @Transactional
    public void enqueueUserStatusChanged(UserStatusChangedEvent event) {
        String payload = writePayload(event);
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateType("User")
                .aggregateId(event.getUserId())
                .type(USER_STATUS_CHANGED_TYPE)
                .payload(payload)
                .status(OutboxStatus.UNPUBLISHED)
                .build();

        OutboxEvent saved = outboxEventRepository.save(outboxEvent);
        registerAfterCommit(saved.getId());
    }

    private void registerAfterCommit(String outboxId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishOutboxEvent(outboxId);
                }
            });
        } else {
            publishOutboxEvent(outboxId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOutboxEvent(String outboxId) {
        outboxEventRepository.findById(outboxId).ifPresent(this::publishIfPending);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishIfPending(OutboxEvent outboxEvent) {
        if (outboxEvent.getStatus() != OutboxStatus.UNPUBLISHED) {
            return;
        }

        try {
            Object payload = objectMapper.readTree(outboxEvent.getPayload());
            String routingKey = resolveRoutingKey(outboxEvent.getType());

            rabbitTemplate.convertAndSend(
                    rabbitMQConfig.userEventsExchange,
                    routingKey,
                    payload
            );

            outboxEvent.setStatus(OutboxStatus.PUBLISHED);
            outboxEventRepository.save(outboxEvent);
            log.info("Outbox event published id={}", outboxEvent.getId());
        } catch (Exception e) {
            log.error("Failed to publish outbox event id={}: {}", outboxEvent.getId(), e.getMessage());
        }
    }

    private String resolveRoutingKey(String type) {
        if (USER_STATUS_CHANGED_TYPE.equals(type)) {
            return rabbitMQConfig.userStatusChangedRoutingKey;
        }
        return type;
    }

    private String writePayload(UserStatusChangedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}
