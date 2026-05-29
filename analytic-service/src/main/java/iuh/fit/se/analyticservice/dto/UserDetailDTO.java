package iuh.fit.se.analyticservice.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailDTO {
    private String id;
    private String phone;
    private String email;
    private String fullName;
    private String avatarUrl;
    private Boolean isActive;
    private Boolean isBlocked;
    private LocalDateTime blockedUntil;
    private String blockedReason;
    private LocalDateTime deletedAt;
}
