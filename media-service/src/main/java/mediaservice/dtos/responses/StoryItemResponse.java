package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.StoryItemType;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StoryItemResponse {
    private String id;
    private StoryItemType type;
    private ImageItemResponse imageItem;
    private VideoItemResponse videoItem;
    private TextItemResponse textItem;
    private boolean isPrimary;
    private int zIndex;
    private double positionX;
    private double positionY;
    private double rotation;
    private double scale;
    private Long startTime;
    private Long endTime;
}

