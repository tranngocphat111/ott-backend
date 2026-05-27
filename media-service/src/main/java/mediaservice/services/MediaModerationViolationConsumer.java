package mediaservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.dtos.events.ContentViolationDetectedEvent;
import mediaservice.models.Content;
import mediaservice.models.Media;
import mediaservice.models.Post;
import mediaservice.models.enums.ContentStatusType;
import mediaservice.realtime.MediaRealtimePublisher;
import mediaservice.repositories.MediaRepository;
import mediaservice.repositories.PostRepository;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaModerationViolationConsumer {

    private static final String MEDIA_SERVICE_SOURCE = "media-service";
    private static final String IMAGE_CONTENT_TYPE = "IMAGE";
    private static final String POST_TARGET_TYPE = "POST";
    private static final String DELETE_OPERATION = "DELETE";

    private final ObjectMapper objectMapper;
    private final MediaRepository mediaRepository;
    private final PostRepository postRepository;
    private final MediaRealtimePublisher mediaRealtimePublisher;

    @Transactional
    @RabbitListener(queues = "${moderation.rabbitmq.queue.violation:media.moderation.violation.queue}")
    public void handleContentViolation(Message message) {
        ContentViolationDetectedEvent event = readEvent(message);

        if (!isMediaImageViolation(event)) {
            log.debug(
                    "[MediaModeration] Ignored violation event: sourceService={}, contentType={}, violationId={}",
                    event.getSourceService(),
                    event.getContentType(),
                    event.getViolationId());
            return;
        }

        String mediaId = normalize(event.getContentRefId());
        if (mediaId == null) {
            throw reject("Missing contentRefId in media moderation violation event");
        }

        try {
            applyViolation(event, mediaId);
        } catch (AmqpRejectAndDontRequeueException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AmqpRejectAndDontRequeueException(
                    "Failed to apply media moderation violation: violationId=" + event.getViolationId(),
                    ex);
        }
    }

    private ContentViolationDetectedEvent readEvent(Message message) {
        try {
            return objectMapper.readValue(message.getBody(), ContentViolationDetectedEvent.class);
        } catch (Exception ex) {
            String payload = message == null ? "" : new String(message.getBody(), StandardCharsets.UTF_8);
            log.warn("[MediaModeration] Invalid violation event payload: {}", payload);
            throw new AmqpRejectAndDontRequeueException("Invalid media moderation violation event", ex);
        }
    }

    private void applyViolation(ContentViolationDetectedEvent event, String mediaId) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> reject("Moderation target media not found: mediaId=" + mediaId));

        String contentId = resolveContentId(media);
        if (contentId == null) {
            log.warn(
                    "[MediaModeration] Violation target media has no content: mediaId={}, violationId={}",
                    mediaId,
                    event.getViolationId());
            return;
        }

        Post post = postRepository.findById(contentId)
                .orElse(null);
        if (post == null) {
            log.warn(
                    "[MediaModeration] Violation target content is not a post: contentId={}, mediaId={}, violationId={}",
                    contentId,
                    mediaId,
                    event.getViolationId());
            return;
        }

        if (post.getStatus() == ContentStatusType.DELETED) {
            log.info(
                    "[MediaModeration] Duplicate violation ignored for deleted post: postId={}, mediaId={}, violationId={}",
                    post.getId(),
                    mediaId,
                    event.getViolationId());
            return;
        }

        post.setStatus(ContentStatusType.DELETED);
        postRepository.save(post);
        publishPostDeletedAfterCommit(post.getId());

        log.warn(
                "[MediaModeration] Post deleted after media violation: postId={}, mediaId={}, violationId={}, labels={}",
                post.getId(),
                mediaId,
                event.getViolationId(),
                event.getMatchedLabels());
    }

    private boolean isMediaImageViolation(ContentViolationDetectedEvent event) {
        if (event == null) {
            return false;
        }
        return MEDIA_SERVICE_SOURCE.equalsIgnoreCase(normalize(event.getSourceService()))
                && IMAGE_CONTENT_TYPE.equalsIgnoreCase(normalize(event.getContentType()));
    }

    private void publishPostDeletedAfterCommit(String postId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            publishPostDeleted(postId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishPostDeleted(postId);
            }
        });
    }

    private void publishPostDeleted(String postId) {
        mediaRealtimePublisher.publish(POST_TARGET_TYPE, postId, DELETE_OPERATION, List.of(), List.of());
    }

    private String resolveContentId(Media media) {
        if (media == null) {
            return null;
        }

        Content content = media.getContent();
        if (content == null) {
            return null;
        }

        return normalize(content.getId());
    }

    private AmqpRejectAndDontRequeueException reject(String message) {
        return new AmqpRejectAndDontRequeueException(message);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
