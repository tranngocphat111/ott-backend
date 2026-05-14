package mediaservice.dtos.requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class BaseStoryItemRequest {
    private String id;
    private boolean isPrimary;
    private int zIndex;
    private double positionX;
    private double positionY;
    private double rotation;
    private double scale;
    private Long startTime;
    private Long endTime;
}

