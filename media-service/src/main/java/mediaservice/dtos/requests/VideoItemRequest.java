package mediaservice.dtos.requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class VideoItemRequest extends BaseStoryItemRequest {
    private String url;
    private String thumbnailUrl;
    private Long duration;
    private int width;
    private int height;
}

