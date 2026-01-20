package iuh.fit.ottbackend.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshRequest {
    private String token;

    private String deviceId;
}
