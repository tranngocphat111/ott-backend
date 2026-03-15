package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.ReactionTargetType;
import mediaservice.models.enums.ReactionType;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReactionResponse {
    private String id;
    private String accountId;
    private String accountUsername;
    private String accountAvatarUrl;
    private String targetId;
    private ReactionTargetType targetType;
    private ReactionType reactionType;
    private LocalDateTime createdAt;
}

