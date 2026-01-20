package iuh.fit.ottbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordChangeResponse {
    private boolean success;
    private String message;
    private int sessionsRevoked;
}
