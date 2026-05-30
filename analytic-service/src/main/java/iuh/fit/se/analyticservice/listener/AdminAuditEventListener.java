package iuh.fit.se.analyticservice.listener;

import java.nio.charset.StandardCharsets;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.analyticservice.config.RabbitMqConfig;
import iuh.fit.se.analyticservice.dto.AdminAuditEvent;
import iuh.fit.se.analyticservice.service.AdminAuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuditEventListener {

    private final AdminAuditLogService adminAuditLogService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMqConfig.ADMIN_AUDIT_QUEUE)
    public void handleAdminAuditEvent(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            AdminAuditEvent event = objectMapper.readValue(payload, AdminAuditEvent.class);
            adminAuditLogService.recordAdminAuditEvent(event);
        } catch (Exception ex) {
            log.error("Failed to process admin audit event. payload={}", payload, ex);
            throw new AmqpRejectAndDontRequeueException("Invalid admin audit event", ex);
        }
    }
}
