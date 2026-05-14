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
    private boolean isPrimary;
    private int zIndex;
    private double positionX;
    private double positionY;
    private double rotation;
    private double scale;
    private Long startTime;
    private Long endTime;
}

