package moderationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import moderationservice.repository.ModerationDecisionRecordRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final ModerationDecisionRecordRepository moderationDecisionRecordRepository;

    public boolean isProcessed(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required for idempotency check");
        }

        boolean processed = moderationDecisionRecordRepository.existsByRequestId(requestId.trim());
        if (processed) {
            log.warn("Duplicate moderation request skipped: requestId={}", requestId);
        }
        return processed;
    }
}
