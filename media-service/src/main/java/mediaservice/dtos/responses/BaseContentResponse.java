package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.ContentStatusType;
import mediaservice.models.enums.VisibilityType;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class BaseContentResponse {
    private String id;
    private String accountId;
    private String accountUsername;
    private String accountDisplayName;
    private String accountAvatarUrl;
    private ContentStatusType status;
    private VisibilityType visibility;
    private List<String> hashTags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

