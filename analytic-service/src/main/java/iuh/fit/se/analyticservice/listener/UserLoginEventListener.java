package iuh.fit.se.analyticservice.listener;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.analyticservice.dto.UserLoginEvent;
import iuh.fit.se.analyticservice.entity.RawLoginEvent;
import iuh.fit.se.analyticservice.repository.RawLoginEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserLoginEventListener {

    private final RawLoginEventRepository rawLoginEventRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "#{@userLoginQueue.name}")
    public void handleUserLoginEvent(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            UserLoginEvent event = objectMapper.readValue(payload, UserLoginEvent.class);
            validateEvent(event);

            RawLoginEvent raw = new RawLoginEvent(
                    event.getEventId(),
                    event.getUserId(),
                    event.getLoginMethod(),
                    event.getTimestamp() != null ? event.getTimestamp() : Instant.now()
            );

            rawLoginEventRepository.save(raw);
            log.info("Saved login event: eventId={}, userId={}", event.getEventId(), event.getUserId());
        } catch (DataIntegrityViolationException duplicate) {
            // event_id is PK -> duplicate means already ingested (idempotent consumer)
            log.warn("Duplicate login event ignored. payload={}", payload);
        } catch (Exception ex) {
            log.error("Failed to parse/save login event payload. payload={}", payload, ex);
            throw new AmqpRejectAndDontRequeueException("Invalid login analytics event", ex);
        }
    }

    private void validateEvent(UserLoginEvent event) {
        if (event == null || event.getEventId() == null || event.getUserId() == null || event.getLoginMethod() == null) {
            throw new IllegalArgumentException("Missing required fields in login event");
        }
    }
}