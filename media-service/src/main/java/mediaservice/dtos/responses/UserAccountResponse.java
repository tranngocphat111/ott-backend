package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class UserAccountResponse extends BaseAccountResponse {
    private boolean isCreator;
    private int totalFollowers;
    private int totalFollowing;
    private int totalPosts;
}

