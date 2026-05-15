package moderationservice.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import moderationservice.contracts.ContentReviewRequest;
import moderationservice.contracts.ContentViolationDetected;
import moderationservice.contracts.ModerationResult;
import moderationservice.entity.ModerationDecisionRecord;
import moderationservice.enums.ModerationDecision;
import moderationservice.repository.ModerationDecisionRecordRepository;
import moderationservice.routing.ModerationRouter;
import moderationservice.service.IdempotencyService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentReviewConsumer {

    private final IdempotencyService idempotencyService;
    private final ModerationRouter moderationRouter;
    private final ModerationDecisionRecordRepository moderationDecisionRecordRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${moderation.rabbitmq.exchange}")
    private String moderationExchange;

    @Value("${moderation.rabbitmq.routing-key.violation-detected}")
    private String violationDetectedRoutingKey;

    @RabbitListener(queues = "${moderation.rabbitmq.queue.review-requests}")
    public void handleContentReviewRequest(ContentReviewRequest request) {
        validateRequestEnvelope(request);

        String requestId = request.getRequestId().trim();
        if (idempotencyService.isProcessed(requestId)) {
            return;
        }

        log.info("Processing moderation request: requestId={}, sourceService={}, contentType={}, contentRefId={}",
                requestId, request.getSourceService(), request.getContentType(), request.getContentRefId());

        ModerationResult result = moderationRouter.route(request);
        moderationDecisionRecordRepository.save(buildDecisionRecord(request, result));

        if (result.getDecision() == ModerationDecision.REJECTED) {
            ContentViolationDetected event = buildViolationEvent(request, result);
            rabbitTemplate.convertAndSend(moderationExchange, violationDetectedRoutingKey, event);
            log.warn("Published content violation event: requestId={}, violationId={}, labels={}",
                    requestId, event.getViolationId(), event.getMatchedLabels());
            return;
        }

        log.info("Moderation request approved without publishing event: requestId={}", requestId);
    }

    private ModerationDecisionRecord buildDecisionRecord(ContentReviewRequest request, ModerationResult result) {
        List<String> matchedLabels = result.getMatchedLabels() == null ? List.of() : result.getMatchedLabels();

        return ModerationDecisionRecord.builder()
                .requestId(request.getRequestId().trim())
                .sourceService(request.getSourceService())
                .contentType(request.getContentType())
                .contentRefId(request.getContentRefId())
                .userId(request.getUserId())
                .tenantId(request.getTenantId())
                .decision(result.getDecision())
                .severity(result.getSeverity())
                .violationType(result.getViolationType())
                .reason(result.getReason())
                .matchedLabels(String.join(",", matchedLabels))
                .evidence(buildEvidence(request))
                .build();
    }

    private ContentViolationDetected buildViolationEvent(ContentReviewRequest request, ModerationResult result) {
        return ContentViolationDetected.builder()
                .violationId(UUID.randomUUID().toString())
                .requestId(request.getRequestId().trim())
                .sourceService(request.getSourceService())
                .contentType(request.getContentType())
                .contentRefId(request.getContentRefId())
                .userId(request.getUserId())
                .tenantId(request.getTenantId())
                .severity(result.getSeverity())
                .violationType(result.getViolationType())
                .matchedLabels(result.getMatchedLabels())
                .evidence(Map.of(
                        "sourceService", request.getSourceService(),
                        "contentRefId", request.getContentRefId()
                ))
                .detectedAt(Instant.now())
                .build();
    }

    private String buildEvidence(ContentReviewRequest request) {
        if (request.getPayload() == null || request.getPayload().isEmpty()) {
            return "{}";
        }
        return request.getPayload().keySet().toString();
    }

    private void validateRequestEnvelope(ContentReviewRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("ContentReviewRequest must not be null");
        }
        if (request.getRequestId() == null || request.getRequestId().isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }
    }
}
