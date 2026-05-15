package moderationservice.contracts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import moderationservice.enums.ModerationDecision;
import moderationservice.enums.ViolationSeverity;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationResult {

    private ModerationDecision decision;
    private ViolationSeverity severity;
    private String violationType;
    private String reason;
    private List<String> matchedLabels;
}
