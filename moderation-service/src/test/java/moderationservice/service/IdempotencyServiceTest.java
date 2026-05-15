package moderationservice.service;

import moderationservice.repository.ModerationDecisionRecordRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyServiceTest {

    private final ModerationDecisionRecordRepository repository = mock(ModerationDecisionRecordRepository.class);
    private final IdempotencyService idempotencyService = new IdempotencyService(repository);

    @Test
    void isProcessedReturnsRepositoryResult() {
        when(repository.existsByRequestId("request-1")).thenReturn(true);

        boolean processed = idempotencyService.isProcessed("request-1");

        assertThat(processed).isTrue();
        verify(repository).existsByRequestId("request-1");
    }

    @Test
    void isProcessedRejectsBlankRequestId() {
        assertThatThrownBy(() -> idempotencyService.isProcessed(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestId");
    }
}
