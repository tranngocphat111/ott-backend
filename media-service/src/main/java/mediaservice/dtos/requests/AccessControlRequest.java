package mediaservice.dtos.requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.RuleType;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccessControlRequest {
    private String accountId;  // ID của account được include/exclude
    private RuleType ruleType;  // INCLUDE hoặc EXCLUDE
}

