package mediaservice.dtos.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContentViolationDetectedEvent {

    private String violationId;
    private String requestId;
    private String sourceService;
    private String contentType;
    private String contentRefId;
    private String userId;
    private String tenantId;
    private String severity;
    private String violationType;
    private List<String> matchedLabels;
    private Map<String, Object> evidence;
    private Instant detectedAt;
}
