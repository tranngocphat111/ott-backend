package mediaservice.dtos.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdatedEvent {
    private String userId;

    private String fullName;
    private String avatarUrl;
    private String coverUrl;
    private String bio;
    private String work;
    private String location;
    private String relationshipStatus;
    private String email;
    private String phone;
}
