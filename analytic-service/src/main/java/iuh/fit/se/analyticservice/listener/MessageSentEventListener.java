package iuh.fit.se.analyticservice.listener;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.analyticservice.config.RabbitMqConfig;
import iuh.fit.se.analyticservice.dto.MessageSentEvent;
import iuh.fit.se.analyticservice.entity.RawMessageEvent;
import iuh.fit.se.analyticservice.repository.RawMessageEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageSentEventListener {

    private final RawMessageEventRepository rawMessageEventRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMqConfig.MESSAGE_SENT_QUEUE)
    public void handleMessageSent(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            MessageSentEvent event = objectMapper.readValue(payload, MessageSentEvent.class);
            validateEvent(event);

            RawMessageEvent raw = new RawMessageEvent(
                    event.getEventId(),
                    event.getMessageId(),
                    event.getUserId(),
                    event.getMessageType(),
                    event.getTimestamp() != null ? event.getTimestamp() : Instant.now()
            );

            rawMessageEventRepository.save(raw);
            log.info("Saved message event: eventId={}, messageType={}", raw.getEventId(), raw.getMessageType());
        } catch (DataIntegrityViolationException duplicate) {
            log.warn("Duplicate message event ignored. payload={}", payload);
        } catch (Exception ex) {
            log.error("Failed to parse/save message sent event. payload={}", payload, ex);
            throw new AmqpRejectAndDontRequeueException("Invalid message analytics event", ex);
        }
    }

    private void validateEvent(MessageSentEvent event) {
        if (event == null || event.getEventId() == null || event.getUserId() == null || event.getMessageType() == null) {
            throw new IllegalArgumentException("Missing required fields in message event");
        }
    }
}
