package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.CreatorProfileStatusType;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreatorProfileResponse {
    private String id;
    private String userId;
    private String username;
    private CreatorProfileStatusType status;
    private boolean isVerified;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
}

