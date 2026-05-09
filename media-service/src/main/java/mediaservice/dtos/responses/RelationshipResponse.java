package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.RelationshipStatusType;
import mediaservice.models.enums.RelationshipType;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RelationshipResponse {
    private String id;
    private String requesterId;
    private String requesterUsername;
    private String requesterDisplayName;
    private String requesterAvatarUrl;
    private String receiverId;
    private String receiverUsername;
    private String receiverDisplayName;
    private String receiverAvatarUrl;
    private RelationshipStatusType status;
    private RelationshipType type;
    private LocalDateTime createdAt;
    private LocalDateTime acceptedAt;
}

