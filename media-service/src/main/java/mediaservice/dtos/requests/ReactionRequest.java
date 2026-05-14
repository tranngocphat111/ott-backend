package mediaservice.dtos.requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.ReactionTargetType;
import mediaservice.models.enums.ReactionType;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReactionRequest {
    private String accountId;
    private String targetId;
    private ReactionTargetType targetType;
    private ReactionType reactionType;
}

