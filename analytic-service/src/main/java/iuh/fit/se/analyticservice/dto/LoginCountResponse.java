package iuh.fit.se.analyticservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginCountResponse {
    private long totalLogins;
}