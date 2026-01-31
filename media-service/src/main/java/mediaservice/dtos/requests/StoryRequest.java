package mediaservice.dtos.requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class StoryRequest extends BaseContentRequest {
    private boolean isHighlight;
    private String highlightName;
    private LocalDateTime expireAt;
    private List<StoryItemRequest> storyItems;
    private List<MusicRequest> musics;  // Story musics
}

