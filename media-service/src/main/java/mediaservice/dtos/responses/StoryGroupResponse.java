package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StoryGroupResponse {
    private String accountId;
    private String accountUsername;
    private String accountDisplayName;
    private String accountAvatarUrl;
    private List<StoryReelItemResponse> stories;
}
