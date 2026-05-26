package moderationservice.contracts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import moderationservice.enums.ViolationSeverity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationRuleRequest {

    private String term;
    private String category;
    private String language;
    private ViolationSeverity severity;
    private Boolean enabled;
}
