package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.RuleType;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ContentAccessControlResponse {
    private String id;
    private String accountId;
    private String accountUsername;
    private String contentId;
    private RuleType ruleType;
    private LocalDateTime createdAt;
}

