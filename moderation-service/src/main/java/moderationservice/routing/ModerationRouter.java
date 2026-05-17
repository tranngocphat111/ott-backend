package moderationservice.routing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import moderationservice.contracts.ContentReviewRequest;
import moderationservice.contracts.ModerationResult;
import moderationservice.enums.ContentType;
import moderationservice.enums.ModerationDecision;
import moderationservice.enums.ViolationSeverity;
import moderationservice.provider.AhoCorasickProfanityProvider;
import moderationservice.provider.RekognitionModerationProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ModerationRouter {

    private static final String TEXT_VIOLATION_TYPE = "TEXT_PROFANITY";
    private static final String IMAGE_VIOLATION_TYPE = "IMAGE_UNSAFE_CONTENT";

    private final AhoCorasickProfanityProvider profanityProvider;
    private final RekognitionModerationProvider rekognitionModerationProvider;

    public ModerationResult route(ContentReviewRequest request) {
        validateRequest(request);

        ContentType contentType = request.getContentType();
        log.info("Routing moderation request: requestId={}, contentType={}, sourceService={}",
                request.getRequestId(), contentType, request.getSourceService());

        if (contentType == ContentType.TEXT) {
            return moderateText(request.getPayload());
        }
        if (contentType == ContentType.IMAGE) {
            return moderateImage(request.getPayload());
        }

        throw new IllegalArgumentException("Unsupported contentType: " + contentType);
    }

    private ModerationResult moderateText(Map<String, Object> payload) {
        String text = requiredPayloadValue(payload, "text");
        List<String> matches = profanityProvider.scanText(text);
        return buildResult(matches, TEXT_VIOLATION_TYPE);
    }

    private ModerationResult moderateImage(Map<String, Object> payload) {
        String bucket = requiredPayloadValue(payload, "bucket");
        String objectKey = requiredPayloadValue(payload, "objectKey");
        List<String> labels;
        try {
            labels = rekognitionModerationProvider.scanImage(bucket, objectKey);
        } catch (RuntimeException ex) {
            log.error("Image moderation provider failed for bucket={}, objectKey={}", bucket, objectKey, ex);
            return ModerationResult.builder()
                    .decision(ModerationDecision.NEEDS_REVIEW)
                    .severity(ViolationSeverity.MEDIUM)
                    .violationType(IMAGE_VIOLATION_TYPE)
                    .reason("Image moderation provider unavailable")
                    .matchedLabels(List.of("REKOGNITION_SCAN_FAILED"))
                    .build();
        }
        return buildResult(labels, IMAGE_VIOLATION_TYPE);
    }

    private ModerationResult buildResult(List<String> matchedLabels, String violationType) {
        if (matchedLabels == null || matchedLabels.isEmpty()) {
            return ModerationResult.builder()
                    .decision(ModerationDecision.APPROVED)
                    .severity(ViolationSeverity.LOW)
                    .violationType(null)
                    .reason("No policy violation detected")
                    .matchedLabels(List.of())
                    .build();
        }

        return ModerationResult.builder()
                .decision(ModerationDecision.REJECTED)
                .severity(ViolationSeverity.HIGH)
                .violationType(violationType)
                .reason("Content violates moderation policy")
                .matchedLabels(matchedLabels)
                .build();
    }

    private void validateRequest(ContentReviewRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("ContentReviewRequest must not be null");
        }
        if (request.getRequestId() == null || request.getRequestId().isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }
        if (request.getSourceService() == null || request.getSourceService().isBlank()) {
            throw new IllegalArgumentException("sourceService is required");
        }
        if (request.getContentType() == null) {
            throw new IllegalArgumentException("contentType is required");
        }
        if (request.getContentRefId() == null || request.getContentRefId().isBlank()) {
            throw new IllegalArgumentException("contentRefId is required");
        }
    }

    private String requiredPayloadValue(Map<String, Object> payload, String key) {
        if (payload == null || !payload.containsKey(key)) {
            throw new IllegalArgumentException("payload." + key + " is required");
        }

        Object value = payload.get(key);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new IllegalArgumentException("payload." + key + " must be a non-blank string");
        }
        return stringValue.trim();
    }
}
