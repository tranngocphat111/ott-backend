package iuh.fit.ottbackend.dto.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestPhoneOtpRequest {
    private String phone;
    private String ipAddress;
}