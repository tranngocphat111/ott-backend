package iuh.fit.authservice.dto.response;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionsResponse {
    private List<SessionInfo> sessions;
    private int total;
}