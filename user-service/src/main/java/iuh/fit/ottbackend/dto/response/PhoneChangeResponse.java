package iuh.fit.ottbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhoneChangeResponse {
    private boolean success;
    private String newPhone;
    private String message;
    private int sessionsRevoked;
}
