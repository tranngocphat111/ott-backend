package mediaservice.dtos.requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.CreatorProfileStatusType;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreatorProfileRequest {
    private String userId;  // UserAccount ID
    private CreatorProfileStatusType status;
    private boolean isVerified;
}

