package iuh.fit.ottbackend.dto.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Enable2FAResponse {
    private boolean enabled;
    private String[] backupCodes;
    private String message;
}