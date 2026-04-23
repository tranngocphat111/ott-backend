package mediaservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.dtos.events.PostCreatedAnalyticsEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${analytics.queue.post-created:analytics.post.created.queue}")
    private String postCreatedQueue;

    public void publishPostCreated(String postId, String userId) {
        try {
            PostCreatedAnalyticsEvent event = PostCreatedAnalyticsEvent.builder()
                    .event_id(UUID.randomUUID().toString())
                    .post_id(postId)
                    .user_id(userId)
                    .timestamp(Instant.now())
                    .build();

            rabbitTemplate.convertAndSend(postCreatedQueue, event);
            log.info("Published analytics post.created: postId={}, userId={}", postId, userId);
        } catch (RuntimeException ex) {
            // Do not break main business flow if analytics pipeline is unavailable
            log.warn("Failed to publish analytics post.created for postId={}: {}", postId, ex.getMessage());
        }
    }
}
