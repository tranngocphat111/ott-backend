package mediaservice.dtos.requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.StoryItemType;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StoryItemRequest {
    private StoryItemType type;  // IMAGE_ITEM, VIDEO_ITEM, TEXT_ITEM
    private ImageItemRequest imageItem;
    private VideoItemRequest videoItem;
    private TextItemRequest textItem;
}

