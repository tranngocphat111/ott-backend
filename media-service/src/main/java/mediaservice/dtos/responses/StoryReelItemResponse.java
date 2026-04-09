package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.VisibilityType;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StoryReelItemResponse {
    private String highlightName;
    private LocalDateTime expireAt;
    private List<StoryItemResponse> storyItems;
    private List<MusicResponse> musics;
    private int totalViews;
    private String accountAvatarUrl;
    private LocalDateTime createdAt;
    private List<String> hashTags;
    private boolean highlight;
    private String id;
    private LocalDateTime updatedAt;
    private VisibilityType visibility;
}
