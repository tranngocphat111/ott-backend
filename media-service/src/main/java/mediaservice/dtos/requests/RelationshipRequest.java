package mediaservice.dtos.requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.RelationshipStatusType;
import mediaservice.models.enums.RelationshipType;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RelationshipRequest {
    private String requesterId;  // UserAccount ID
    private String receiverId;  // UserAccount ID
    private RelationshipStatusType status;
    private RelationshipType type;
}

