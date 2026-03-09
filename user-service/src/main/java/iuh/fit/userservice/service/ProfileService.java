package iuh.fit.userservice.service;

import iuh.fit.userservice.dto.request.UpdateProfileRequest;
import iuh.fit.userservice.dto.response.UserProfileResponse;
import iuh.fit.userservice.entity.User;
import iuh.fit.userservice.exception.AppException;
import iuh.fit.userservice.exception.ErrorCode;
import iuh.fit.userservice.mapper.UserMapper;
import iuh.fit.userservice.repository.UserRepository;
import iuh.fit.userservice.utils.ValidationUtils;
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
        if (user.getDeletedAt() != null) throw new AppException(ErrorCode.ACCOUNT_DELETED);
        return userMapper.toUserProfileResponse(user);
    }

    public UserProfileResponse getPublicProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (user.getDeletedAt() != null || !user.getIsActive()) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        return userMapper.toUserProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        validateUserStatus(user);

        if (request.getFullName() != null) {
            String name = validationUtils.sanitizeString(request.getFullName());
            if (name.isEmpty() || name.length() > 100) throw new AppException(ErrorCode.INVALID_FULL_NAME);
            user.setFullName(name);
        }
        if (request.getBio() != null) {
            String bio = validationUtils.sanitizeString(request.getBio());
            if (bio.length() > 500) throw new AppException(ErrorCode.BIO_TOO_LONG);
            user.setBio(bio);
        }
        if (request.getDateOfBirth() != null) {
            if (request.getDateOfBirth().isAfter(java.time.LocalDate.now()))
                throw new AppException(ErrorCode.INVALID_DATE_OF_BIRTH);
            user.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getGender()    != null) user.setGender(request.getGender());
        if (request.getAvatarUrl() != null) {
            if (!isValidUrl(request.getAvatarUrl())) throw new AppException(ErrorCode.INVALID_AVATAR_URL);
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getCoverUrl()  != null) {
            if (!isValidUrl(request.getCoverUrl()))  throw new AppException(ErrorCode.INVALID_COVER_URL);
            user.setCoverUrl(request.getCoverUrl());
        }

        return userMapper.toUserProfileResponse(userRepository.save(user));
    }

    private void validateUserStatus(User user) {
        if (user.getDeletedAt() != null) throw new AppException(ErrorCode.ACCOUNT_DELETED);
        if (!user.getIsActive())         throw new AppException(ErrorCode.USER_NOT_ACTIVE);
        if (user.getIsBlocked()) {
            if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(LocalDateTime.now()))
                throw new AppException(ErrorCode.USER_BLOCKED);
            user.setIsBlocked(false); user.setBlockedUntil(null); user.setBlockedReason(null);
            userRepository.save(user);
        }
    }

    private boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        return url.matches("^(https?://)?([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(/.*)?$");
    }
}