package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class BaseAccountResponse {
    private String id;
    private String username;
    private String displayName;
    private String email;
    private String avatarUrl;
    private String coverUrl;
    private String phoneNumber;
    private String bio;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

