package moderationservice.contracts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import moderationservice.enums.ContentType;
import moderationservice.enums.ViolationSeverity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentViolationDetected {

    private String violationId;
    private String requestId;
    private String sourceService;
    private ContentType contentType;
    private String contentRefId;
    private String userId;
    private String tenantId;
    private ViolationSeverity severity;
    private String violationType;
    private List<String> matchedLabels;
    private Map<String, Object> evidence;
    private Instant detectedAt;
}
