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
    private boolean isPhoneVerified;
    private boolean isEmailVerified;
    private boolean hasPassword;
    private boolean hasGoogleLinked;
    private boolean is2FAEnabled;
    private LocalDateTime createdAt;
}