package iuh.fit.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailChangeResponse {
    private Boolean success;
    private String newEmail;
    private String message;
    private int sessionsRevoked;

    private boolean googleUnlinked;
}
