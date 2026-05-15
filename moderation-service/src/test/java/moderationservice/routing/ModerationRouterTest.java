package moderationservice.routing;

import moderationservice.contracts.ContentReviewRequest;
import moderationservice.contracts.ModerationResult;
import moderationservice.enums.ContentType;
import moderationservice.enums.ModerationDecision;
import moderationservice.provider.AhoCorasickProfanityProvider;
import moderationservice.provider.RekognitionModerationProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationRouterTest {

    private final AhoCorasickProfanityProvider profanityProvider = mock(AhoCorasickProfanityProvider.class);
    private final RekognitionModerationProvider rekognitionProvider = mock(RekognitionModerationProvider.class);
    private final ModerationRouter moderationRouter = new ModerationRouter(profanityProvider, rekognitionProvider);

    @Test
    void routeTextReturnsRejectedWhenProfanityMatches() {
        when(profanityProvider.scanText("unsafe text")).thenReturn(List.of("unsafe"));

        ModerationResult result = moderationRouter.route(ContentReviewRequest.builder()
                .requestId("request-1")
                .sourceService("chat-service")
                .contentType(ContentType.TEXT)
                .contentRefId("message-1")
                .payload(Map.of("text", "unsafe text"))
                .build());

        assertThat(result.getDecision()).isEqualTo(ModerationDecision.REJECTED);
        assertThat(result.getMatchedLabels()).containsExactly("unsafe");
        verify(profanityProvider).scanText("unsafe text");
    }

    @Test
    void routeImageExtractsBucketAndObjectKey() {
        when(rekognitionProvider.scanImage("media-bucket", "images/1.png")).thenReturn(List.of());

        ModerationResult result = moderationRouter.route(ContentReviewRequest.builder()
                .requestId("request-2")
                .sourceService("media-service")
                .contentType(ContentType.IMAGE)
                .contentRefId("media-1")
                .payload(Map.of("bucket", "media-bucket", "objectKey", "images/1.png"))
                .build());

        assertThat(result.getDecision()).isEqualTo(ModerationDecision.APPROVED);
        verify(rekognitionProvider).scanImage("media-bucket", "images/1.png");
    }
}
