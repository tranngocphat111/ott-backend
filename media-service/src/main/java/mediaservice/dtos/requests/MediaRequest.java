package mediaservice.dtos.requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.MediaType;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MediaRequest {
    private MediaType type;  // IMAGE_MEDIA hoặc VIDEO_MEDIA
    private String url;
    private String caption;
    private int orderIndex;

    // Chỉ dành cho video
    private String thumbnailUrl;
    private Long duration;
    private Boolean hasAudio;
}

