package iuh.fit.se.analyticservice.listener;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.analyticservice.dto.UserRegisteredEvent;
import iuh.fit.se.analyticservice.entity.RawUserEvent;
import iuh.fit.se.analyticservice.repository.RawUserEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredEventListener {

    private final RawUserEventRepository rawUserEventRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "#{@userRegisteredQueue.name}")
    public void handleUserRegisteredEvent(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            UserRegisteredEvent event = objectMapper.readValue(payload, UserRegisteredEvent.class);
            validateEvent(event);

            RawUserEvent raw = new RawUserEvent(
                    event.getEventId(),
                    event.getUserId(),
                    event.getRegisterMethod(),
                    event.getTimestamp() != null ? event.getTimestamp() : Instant.now()
            );

            rawUserEventRepository.save(raw);
            log.info("Saved registration event: eventId={}, userId={}", event.getEventId(), event.getUserId());
        } catch (DataIntegrityViolationException duplicate) {
            log.warn("Duplicate registration event ignored. payload={}", payload);
        } catch (Exception ex) {
            log.error("Failed to parse/save registration event payload. payload={}", payload, ex);
            throw new AmqpRejectAndDontRequeueException("Invalid registration analytics event", ex);
        }
    }

    private void validateEvent(UserRegisteredEvent event) {
        if (event == null || event.getEventId() == null || event.getUserId() == null || event.getRegisterMethod() == null) {
            throw new IllegalArgumentException("Missing required fields in registration event");
        }
    }
}
