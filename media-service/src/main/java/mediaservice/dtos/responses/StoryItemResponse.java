package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.StoryItemType;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StoryItemResponse {
    private StoryItemType type;
    private ImageItemResponse imageItem;
    private VideoItemResponse videoItem;
    private TextItemResponse textItem;
}

