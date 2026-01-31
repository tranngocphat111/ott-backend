package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.FollowTargetType;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FollowResponse {
    private String id;
    private String followerId;
    private String followerUsername;
    private String followerAvatarUrl;
    private FollowTargetType targetType;
    private String targetId;
    private LocalDateTime createdAt;
}

