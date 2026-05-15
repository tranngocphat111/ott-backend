package mediaservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.dtos.events.ContentReviewRequest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaModerationPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${moderation.rabbitmq.exchange:moderation.events}")
    private String moderationExchange;

    @Value("${moderation.rabbitmq.routing-key.review-request:moderation.review.requests.queue}")
    private String reviewRequestRoutingKey;

    public void publishImageForReview(String mediaId, String uploaderId, String bucketName, String objectKey) {
        try {
            if (isBlank(mediaId) || isBlank(bucketName) || isBlank(objectKey)) {
                log.warn(
                        "[Moderation] Skipped image review publish because mediaId, bucketName, or objectKey is blank: mediaId={}",
                        mediaId);
                return;
            }

            ContentReviewRequest request = ContentReviewRequest.builder()
                    .requestId("media-image:" + mediaId.trim())
                    .sourceService("media-service")
                    .eventType("media.uploaded")
                    .contentType("IMAGE")
                    .contentRefId(mediaId.trim())
                    .userId(normalizeNullable(uploaderId))
                    .tenantId("default")
                    .payload(Map.of(
                            "bucket", bucketName.trim(),
                            "objectKey", objectKey.trim()
                    ))
                    .metadata(Map.of())
                    .createdAt(Instant.now())
                    .build();

            rabbitTemplate.convertAndSend(moderationExchange, reviewRequestRoutingKey, request);
            log.info("[Moderation] Published image review request: requestId={}, mediaId={}, objectKey={}",
                    request.getRequestId(), mediaId, objectKey);
        } catch (RuntimeException ex) {
            log.warn("[Moderation] Failed to publish image review request for mediaId={}: {}",
                    mediaId, ex.getMessage());
        }
    }

    private String normalizeNullable(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
