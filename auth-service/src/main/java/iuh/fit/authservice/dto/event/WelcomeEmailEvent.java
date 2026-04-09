package iuh.fit.authservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WelcomeEmailEvent {
    private String userId;
    private String email;
    private String fullName;
    private String phone;
    private boolean hasPassword;
    private boolean hasGoogleLinked;
}