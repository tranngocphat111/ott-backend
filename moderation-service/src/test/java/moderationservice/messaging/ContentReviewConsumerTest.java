package moderationservice.messaging;

import moderationservice.contracts.ContentReviewRequest;
import moderationservice.contracts.ModerationResult;
import moderationservice.entity.ModerationDecisionRecord;
import moderationservice.enums.ContentType;
import moderationservice.enums.ModerationDecision;
import moderationservice.enums.ViolationSeverity;
import moderationservice.repository.ModerationDecisionRecordRepository;
import moderationservice.routing.ModerationRouter;
import moderationservice.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentReviewConsumerTest {

    private final IdempotencyService idempotencyService = mock(IdempotencyService.class);
    private final ModerationRouter moderationRouter = mock(ModerationRouter.class);
    private final ModerationDecisionRecordRepository repository = mock(ModerationDecisionRecordRepository.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final ContentReviewConsumer consumer = new ContentReviewConsumer(
            idempotencyService,
            moderationRouter,
            repository,
            rabbitTemplate
    );

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(consumer, "moderationExchange", "moderation.events");
        ReflectionTestUtils.setField(consumer, "violationDetectedRoutingKey", "moderation.violation.detected");
    }

    @Test
    void handleContentReviewRequestSavesApprovedDecisionWithoutPublishing() {
        ContentReviewRequest request = request();
        when(idempotencyService.isProcessed("request-1")).thenReturn(false);
        when(moderationRouter.route(request)).thenReturn(ModerationResult.builder()
                .decision(ModerationDecision.APPROVED)
                .severity(ViolationSeverity.LOW)
                .reason("No policy violation detected")
                .matchedLabels(List.of())
                .build());

        consumer.handleContentReviewRequest(request);

        verify(repository).save(any(ModerationDecisionRecord.class));
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void handleContentReviewRequestPublishesRejectedDecision() {
        ContentReviewRequest request = request();
        when(idempotencyService.isProcessed("request-1")).thenReturn(false);
        when(moderationRouter.route(request)).thenReturn(ModerationResult.builder()
                .decision(ModerationDecision.REJECTED)
                .severity(ViolationSeverity.HIGH)
                .violationType("TEXT_PROFANITY")
                .reason("Content violates moderation policy")
                .matchedLabels(List.of("unsafe"))
                .build());

        consumer.handleContentReviewRequest(request);

        verify(repository).save(any(ModerationDecisionRecord.class));
        verify(rabbitTemplate).convertAndSend(
                eq("moderation.events"),
                eq("moderation.violation.detected"),
                any(Object.class)
        );
    }

    @Test
    void handleContentReviewRequestSkipsDuplicate() {
        ContentReviewRequest request = request();
        when(idempotencyService.isProcessed("request-1")).thenReturn(true);

        consumer.handleContentReviewRequest(request);

        verify(moderationRouter, never()).route(any(ContentReviewRequest.class));
        verify(repository, never()).save(any(ModerationDecisionRecord.class));
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    private ContentReviewRequest request() {
        return ContentReviewRequest.builder()
                .requestId("request-1")
                .sourceService("chat-service")
                .contentType(ContentType.TEXT)
                .contentRefId("message-1")
                .payload(Map.of("text", "hello"))
                .build();
    }
}
