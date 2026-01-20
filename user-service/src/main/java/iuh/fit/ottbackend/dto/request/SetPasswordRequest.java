package iuh.fit.ottbackend.dto.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetPasswordRequest {
    private String password;

    private String confirmPassword;
}