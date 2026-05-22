package iuh.fit.notificationservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PushTokenRequest {
    @NotBlank
    private String userId;

    private String token;

    private String platform;
    private String deviceId;
}
