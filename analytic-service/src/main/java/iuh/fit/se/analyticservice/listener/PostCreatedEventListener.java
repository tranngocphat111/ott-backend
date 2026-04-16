package iuh.fit.se.analyticservice.listener;

import java.nio.charset.StandardCharsets;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
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
        try {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            PostCreatedEvent event = objectMapper.readValue(payload, PostCreatedEvent.class);

            RawPostEvent raw = new RawPostEvent(
                    event.getEventId(),
                    event.getPostId(),
                    event.getUserId(),
                    event.getTimestamp()
            );

            rawPostEventRepository.save(raw);
            log.info("Saved post event: eventId={}, postId={}", raw.getEventId(), raw.getPostId());
        } catch (Exception ex) {
            log.error("Failed to parse/save post created event", ex);
        }
    }
}
