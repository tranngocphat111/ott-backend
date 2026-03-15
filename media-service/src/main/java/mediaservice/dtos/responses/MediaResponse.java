package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.MediaType;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MediaResponse {
    private String id;
    private MediaType type;
    private String url;
    private String caption;
    private int orderIndex;

    // For video
    private String thumbnailUrl;
    private Long duration;
    private Boolean hasAudio;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

