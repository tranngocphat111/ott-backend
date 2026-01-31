package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.MentionTargetType;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MentionResponse {
    private String id;
    private MentionTargetType targetType;
    private String targetId;
    private String taggedAccountId;
    private String taggedAccountUsername;
    private String taggedAccountAvatarUrl;
    private String taggedByAccountId;
    private String taggedByAccountUsername;
    private LocalDateTime createdAt;
}

