package iuh.fit.ottbackend.dto.request;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestEmailOtpRequest {
    private String email;
    private String ipAddress;
}