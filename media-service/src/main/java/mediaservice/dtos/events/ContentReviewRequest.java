package mediaservice.dtos.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentReviewRequest {

    private String requestId;
    private String sourceService;
    private String eventType;
    private String contentType;
    private String contentRefId;
    private String userId;
    private String tenantId;
    private Map<String, Object> payload;
    private Map<String, Object> metadata;
    private Instant createdAt;
}
