package iuh.fit.userservice.dto.response;

import iuh.fit.userservice.entity.enums.AccountType;
import iuh.fit.userservice.entity.enums.Gender;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private String id;
    private String phone;
    private String email;
    private String googleId;
    private String fullName;
    private String avatarUrl;
    private String coverUrl;
    private String bio;
    private String dateOfBirth;
    private Gender gender;
    private AccountType accountType;
    private boolean isPhoneVerified;
    private boolean isEmailVerified;
    private boolean hasPassword;
    private boolean hasGoogleLinked;
    private boolean is2FAEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private LocalDateTime passwordChangedAt;
    private LocalDateTime emailChangedAt;
    private LocalDateTime phoneChangedAt;
}