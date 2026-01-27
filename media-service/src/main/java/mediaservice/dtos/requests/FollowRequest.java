package mediaservice.dtos.requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.FollowTargetType;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FollowRequest {
    private String followerId;  // UserAccount ID
    private FollowTargetType targetType;
    private String targetId;
}

