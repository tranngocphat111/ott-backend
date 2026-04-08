package iuh.fit.userservice.dto.response;

import iuh.fit.userservice.entity.enums.AccountType;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String id;
    private String phone;
    private String email;
    private String googleId;
    private String fullName;
    private String avatarUrl;
    private AccountType accountType;
    private Boolean isPhoneVerified;
    private Boolean isEmailVerified;
    private Boolean hasPassword;
    private Boolean hasGoogleLinked;
    private Boolean is2FAEnabled;
    private LocalDateTime createdAt;
    private String coverUrl;

    private Boolean isActive;
    private Boolean isBlocked;
    private LocalDateTime blockedUntil;
    private String blockedReason;
    private LocalDateTime deletedAt;
    private Boolean isFirstLogin;
    private Boolean welcomeEmailSent;
    private LocalDateTime lastLoginAt;
}