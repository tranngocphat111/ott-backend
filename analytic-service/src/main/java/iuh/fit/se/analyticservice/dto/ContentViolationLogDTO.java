package iuh.fit.se.analyticservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ContentViolationLogDTO(
        UUID id,
        String violationId,
        String sourceService,
        String contentType,
        String contentRefId,
        String userId,
        String severity,
        String violationType,
        String matchedLabels,
        LocalDateTime detectedAt,
        LocalDateTime loggedAt
) {
}
