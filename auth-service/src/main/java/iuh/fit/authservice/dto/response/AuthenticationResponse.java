package iuh.fit.authservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationResponse {
    private String token;
    private String refreshToken;
    private boolean authenticated;
    private boolean requires2FA;
    private boolean requiresPhoneSetup;
    private String tempToken;
    private GoogleUserInfo googleUserInfo;
}