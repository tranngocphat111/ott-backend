package iuh.fit.ottbackend.dto.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkEmailRequest {
    private String email;
    private String otp;
    private String ipAddress;
}