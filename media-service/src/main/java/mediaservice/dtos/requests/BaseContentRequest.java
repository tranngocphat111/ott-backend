package mediaservice.dtos.requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.VisibilityType;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class BaseContentRequest {
    private String userId;  // Account ID
    private VisibilityType visibility;
    private List<String> hashTags;
    private List<AccessControlRequest> accessControls;
    private List<MentionRequest> mentions;
}

