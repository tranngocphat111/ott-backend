package moderationservice.contracts;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditEvent {
    private String eventId;
    private String actorId;
    private String actionType;
    private String targetType;
    private String targetId;
    private String reason;
    private String oldValue;
    private String newValue;
    private Instant timestamp;
}
