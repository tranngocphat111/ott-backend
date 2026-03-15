package mediaservice.dtos.requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VideoMediaRequest {
    private String url;
    private String caption;
    private int orderIndex;
    private String thumbnailUrl;
    private Long duration;
    private boolean hasAudio;
}

