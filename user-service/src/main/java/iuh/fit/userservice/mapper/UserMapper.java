package iuh.fit.userservice.mapper;

import iuh.fit.userservice.dto.response.UserProfileResponse;
import iuh.fit.userservice.dto.response.UserResponse;
import iuh.fit.userservice.entity.User;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
public class UserMapper {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public UserResponse toUserResponse(User user) {
        if (user == null) return null;
        return UserResponse.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .email(user.getEmail())
                .googleId(user.getGoogleId())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .accountType(user.getAccountType())
                .isPhoneVerified(user.getIsPhoneVerified() != null && user.getIsPhoneVerified())
                .isEmailVerified(user.getIsEmailVerified() != null && user.getIsEmailVerified())
                .hasPassword(user.getPasswordHash() != null && !user.getPasswordHash().isEmpty())
                .hasGoogleLinked(user.getGoogleId() != null && !user.getGoogleId().isEmpty())
                .is2FAEnabled(user.getTwoFactorAuth() != null
                        && user.getTwoFactorAuth().getIsEnabled() != null
                        && user.getTwoFactorAuth().getIsEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public UserProfileResponse toUserProfileResponse(User user) {
        if (user == null) return null;
        return UserProfileResponse.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .email(user.getEmail())
                .googleId(user.getGoogleId())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .coverUrl(user.getCoverUrl())
                .bio(user.getBio())
                .dateOfBirth(user.getDateOfBirth() != null ? user.getDateOfBirth().format(DATE_FORMATTER) : null)
                .gender(user.getGender())
                .accountType(user.getAccountType())
                .isPhoneVerified(user.getIsPhoneVerified() != null && user.getIsPhoneVerified())
                .isEmailVerified(user.getIsEmailVerified() != null && user.getIsEmailVerified())
                .hasPassword(user.getPasswordHash() != null && !user.getPasswordHash().isEmpty())
                .hasGoogleLinked(user.getGoogleId() != null && !user.getGoogleId().isEmpty())
                .is2FAEnabled(user.getTwoFactorAuth() != null
                        && user.getTwoFactorAuth().getIsEnabled() != null
                        && user.getTwoFactorAuth().getIsEnabled())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .passwordChangedAt(user.getPasswordChangedAt())
                .emailChangedAt(user.getEmailChangedAt())
                .phoneChangedAt(user.getPhoneChangedAt())
                .build();
    }
}