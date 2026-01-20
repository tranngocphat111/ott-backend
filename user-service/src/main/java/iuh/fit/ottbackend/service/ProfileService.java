package iuh.fit.ottbackend.service;

import iuh.fit.ottbackend.dto.request.UpdateProfileRequest;
import iuh.fit.ottbackend.dto.response.UserProfileResponse;
import iuh.fit.ottbackend.entity.User;
import iuh.fit.ottbackend.exception.AppException;
import iuh.fit.ottbackend.exception.ErrorCode;
import iuh.fit.ottbackend.mapper.UserMapper;
import iuh.fit.ottbackend.repository.UserRepository;
import iuh.fit.ottbackend.utils.ValidationUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final ValidationUtils validationUtils;

    public UserProfileResponse getUserProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getDeletedAt() != null) {
            throw new AppException(ErrorCode.ACCOUNT_DELETED);
        }

        return userMapper.toUserProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        validateUserStatus(user);

        if (request.getFullName() != null) {
            String sanitizedName = validationUtils.sanitizeString(request.getFullName());
            if (sanitizedName.isEmpty() || sanitizedName.length() > 100) {
                throw new AppException(ErrorCode.INVALID_FULL_NAME);
            }
            user.setFullName(sanitizedName);
        }

        if (request.getBio() != null) {
            String sanitizedBio = validationUtils.sanitizeString(request.getBio());
            if (sanitizedBio.length() > 500) {
                throw new AppException(ErrorCode.BIO_TOO_LONG);
            }
            user.setBio(sanitizedBio);
        }

        if (request.getDateOfBirth() != null) {
            if (request.getDateOfBirth().isAfter(java.time.LocalDate.now())) {
                throw new AppException(ErrorCode.INVALID_DATE_OF_BIRTH);
            }
            user.setDateOfBirth(request.getDateOfBirth());
        }

        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }

        if (request.getAvatarUrl() != null) {
            if (!isValidUrl(request.getAvatarUrl())) {
                throw new AppException(ErrorCode.INVALID_AVATAR_URL);
            }
            user.setAvatarUrl(request.getAvatarUrl());
        }

        if (request.getCoverUrl() != null) {
            if (!isValidUrl(request.getCoverUrl())) {
                throw new AppException(ErrorCode.INVALID_COVER_URL);
            }
            user.setCoverUrl(request.getCoverUrl());
        }

        user = userRepository.save(user);

        return userMapper.toUserProfileResponse(user);
    }

    private void validateUserStatus(User user) {
        if (user.getDeletedAt() != null) {
            throw new AppException(ErrorCode.ACCOUNT_DELETED);
        }

        if (!user.getIsActive()) {
            throw new AppException(ErrorCode.USER_NOT_ACTIVE);
        }

        if (user.getIsBlocked()) {
            if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(LocalDateTime.now())) {
                throw new AppException(ErrorCode.USER_BLOCKED);
            }
            user.setIsBlocked(false);
            user.setBlockedUntil(null);
            user.setBlockedReason(null);
            userRepository.save(user);
        }
    }

    private boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        String urlRegex = "^(https?://)?([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(/.*)?$";
        return url.matches(urlRegex);
    }
}