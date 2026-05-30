package iuh.fit.se.analyticservice.listener;

import java.nio.charset.StandardCharsets;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.analyticservice.config.RabbitMqConfig;
import iuh.fit.se.analyticservice.dto.UserStatusChangedEvent;
import iuh.fit.se.analyticservice.service.AdminAuditLogService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationEventListener {

    private final AdminAuditLogService adminAuditLogService;
    private final ObjectMapper objectMapper;

    @PostConstruct
    void logListenerMode() {
        log.info("User status audit listener initialized with raw RabbitMQ Message handler");
    }

    @RabbitListener(queues = RabbitMqConfig.USER_STATUS_CHANGED_QUEUE)
    public void handleUserStatusChangedEvent(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            // Bước 1: Khử tuần tự hóa payload an toàn
            UserStatusChangedEvent event = objectMapper.readValue(payload, UserStatusChangedEvent.class);
            
            log.info("Received user.status.changed event from broker: userId={}, actionType={}", 
                    event.getUserId(), event.getActionType());

            // Bước 2: Ủy thác toàn bộ logic phức tạp (Idempotency, Mapping, Postgres Write) cho Service Layer
            adminAuditLogService.recordUserStatusChanged(event);

        } catch (Exception ex) {
            log.error("Failed to process user.status.changed event. payload={}", payload, ex);
            // Kích hoạt cờ chống requeue vô hạn để đẩy bản tin lỗi xuống DLQ
            throw new AmqpRejectAndDontRequeueException("Invalid moderation analytics event", ex);
        }
    }
}