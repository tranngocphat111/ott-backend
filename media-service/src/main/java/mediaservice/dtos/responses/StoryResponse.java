package mediaservice.dtos.responses;

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
public class StoryResponse extends BaseContentResponse {
    private boolean isHighlight;
    private String highlightName;
    private LocalDateTime expireAt;
    private List<StoryItemResponse> storyItems;
    private List<MusicResponse> musics;
    private int totalViews;
}

