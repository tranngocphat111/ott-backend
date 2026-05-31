package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.MediaModerationStatus;
import mediaservice.models.enums.MediaType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MediaResponse {
    private String id;
    private MediaType type;
    private String url;
    private String caption;
    private int orderIndex;
    private MediaModerationStatus moderationStatus;
    private String moderationSeverity;
    private String moderationViolationType;
    private List<String> moderationMatchedLabels;
    private String moderationReason;
    private Instant moderationDetectedAt;

    // For video
    private String thumbnailUrl;
    private Long duration;
    private Boolean hasAudio;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

