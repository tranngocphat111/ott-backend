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
import iuh.fit.se.analyticservice.dto.PostCreatedEvent;
import iuh.fit.se.analyticservice.entity.RawPostEvent;
import iuh.fit.se.analyticservice.repository.RawPostEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostCreatedEventListener {

    private final RawPostEventRepository rawPostEventRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMqConfig.POST_CREATED_QUEUE)
    public void handlePostCreated(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            PostCreatedEvent event = objectMapper.readValue(payload, PostCreatedEvent.class);
            validateEvent(event);

            RawPostEvent raw = new RawPostEvent(
                    event.getEventId(),
                    event.getPostId(),
                    event.getUserId(),
                    event.getTimestamp() != null ? event.getTimestamp() : Instant.now()
            );

            rawPostEventRepository.save(raw);
            log.info("Saved post event: eventId={}, postId={}", raw.getEventId(), raw.getPostId());
        } catch (DataIntegrityViolationException duplicate) {
            log.warn("Duplicate post event ignored. payload={}", payload);
        } catch (Exception ex) {
            log.error("Failed to parse/save post created event. payload={}", payload, ex);
            throw new AmqpRejectAndDontRequeueException("Invalid post analytics event", ex);
        }
    }

    private void validateEvent(PostCreatedEvent event) {
        if (event == null || event.getEventId() == null || event.getPostId() == null || event.getUserId() == null) {
            throw new IllegalArgumentException("Missing required fields in post event");
        }
    }
}
