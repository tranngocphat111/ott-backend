package iuh.fit.ottbackend.dto.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkPhoneRequest {
    private String phone;
    private String otp;
    private String ipAddress;
}