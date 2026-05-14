package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.OfficialAccountStatusType;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class OfficialAccountResponse extends BaseAccountResponse {
    private String ownerUserId;
    private String ownerUsername;
    private OfficialAccountStatusType status;
    private boolean isVerified;
    private int totalFollowers;
}

