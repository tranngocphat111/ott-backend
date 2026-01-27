package mediaservice.dtos.requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.OfficialAccountStatusType;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class OfficialAccountRequest extends BaseAccountRequest {
    private String ownerUserId;
    private OfficialAccountStatusType status;
    private boolean isVerified;
}

