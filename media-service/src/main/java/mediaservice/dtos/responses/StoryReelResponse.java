package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StoryReelResponse {
    private List<StoryGroupResponse> storyGroups;
    private List<UserAccountResponse> suggestedUsers;
}
