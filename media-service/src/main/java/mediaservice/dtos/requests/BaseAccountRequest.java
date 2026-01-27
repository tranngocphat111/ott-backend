package mediaservice.dtos.requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class BaseAccountRequest {
    private String username;
    private String displayName;
    private String email;
    private String avatarUrl;
    private String coverUrl;
    private String bio;
}

