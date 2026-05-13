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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final ValidationUtils validationUtils;
    private final UserEventPublisher userEventPublisher;

    public UserProfileResponse getUserProfile(String userId) {
        log.debug("Fetching profile for userId: {}", userId);

        User user = userRepository.findByIdWithTwoFactorAuth(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getDeletedAt() != null) {
            log.warn("Deleted account attempted to access profile - userId: {}", userId);
            throw new AppException(ErrorCode.ACCOUNT_DELETED);
        }

        log.info("Profile retrieved successfully for userId: {}", userId);
        return userMapper.toUserProfileResponse(user);
    }

    public UserProfileResponse getPublicProfile(String userId) {
        log.debug("Fetching public profile for userId: {}", userId);

        User user = userRepository.findByIdWithTwoFactorAuth(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getDeletedAt() != null || !user.getIsActive()) {
            log.warn("Public profile access denied (deleted/inactive) - userId: {}", userId);
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        log.debug("Public profile retrieved for userId: {}", userId);
        return userMapper.toUserProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        log.info("Updating profile for userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        validateUserStatus(user);

        boolean hasChanges = false;

        if (request.getFullName() != null) {
            String name = validationUtils.sanitizeString(request.getFullName());
            if (name.isEmpty() || name.length() > 100) {
                log.warn("Invalid full name length for userId: {}", userId);
                throw new AppException(ErrorCode.INVALID_FULL_NAME);
            }
            user.setFullName(name);
            hasChanges = true;
        }

        if (request.getBio() != null) {
            String bio = validationUtils.sanitizeString(request.getBio());
            if (bio.length() > 500) {
                log.warn("Bio too long for userId: {}", userId);
                throw new AppException(ErrorCode.BIO_TOO_LONG);
            }
            user.setBio(bio);
            hasChanges = true;
        }

        if (request.getWork() != null) {
            String work = validationUtils.sanitizeString(request.getWork());
            user.setWork(work);
            hasChanges = true;
        }

        if (request.getLocation() != null) {
            String location = validationUtils.sanitizeString(request.getLocation());
            user.setLocation(location);
            hasChanges = true;
        }

        if (request.getRelationshipStatus() != null) {
            String status = validationUtils.sanitizeString(request.getRelationshipStatus());
            user.setRelationshipStatus(status);
            hasChanges = true;
        }

        if (request.getDateOfBirth() != null) {
            if (request.getDateOfBirth().isAfter(java.time.LocalDate.now())) {
                log.warn("Invalid date of birth (future date) for userId: {}", userId);
                throw new AppException(ErrorCode.INVALID_DATE_OF_BIRTH);
            }
            user.setDateOfBirth(request.getDateOfBirth());
            hasChanges = true;
        }

        if (request.getGender() != null) {
            user.setGender(request.getGender());
            hasChanges = true;
        }

        if (hasChanges) {
            user = userRepository.save(user);

            // Broadcast update - unified event with all profile fields
            userEventPublisher.publishUserUpdated(
                    iuh.fit.userservice.dto.event.UserUpdatedEvent.builder()
                            .userId(user.getId())
                            .fullName(user.getFullName())
                            .displayName(user.getFullName())
                            .avatarUrl(user.getAvatarUrl())
                            .avatar(user.getAvatarUrl())
                            .coverUrl(user.getCoverUrl())
                            .bio(user.getBio())
                            .work(user.getWork())
                            .location(user.getLocation())
                            .relationshipStatus(user.getRelationshipStatus())
                            .email(user.getEmail())
                            .phone(user.getPhone())
                            .build());

            log.info("Profile updated successfully for userId: {}", userId);
        } else {
            log.debug("No changes in profile update request for userId: {}", userId);
        }

        return userMapper.toUserProfileResponse(user);
    }

    private void validateUserStatus(User user) {
        if (user.getDeletedAt() != null) {
            log.warn("Deleted account attempted profile update - userId: {}", user.getId());
            throw new AppException(ErrorCode.ACCOUNT_DELETED);
        }
        if (!user.getIsActive()) {
            log.warn("Inactive account attempted profile update - userId: {}", user.getId());
            throw new AppException(ErrorCode.USER_NOT_ACTIVE);
        }
        if (user.getIsBlocked()) {
            if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(LocalDateTime.now())) {
                log.warn("Blocked account attempted profile update - userId: {}", user.getId());
                throw new AppException(ErrorCode.USER_BLOCKED);
            }
            log.info("Auto-unblocking user due to expired block - userId: {}", user.getId());
            user.setIsBlocked(false);
            user.setBlockedUntil(null);
            user.setBlockedReason(null);
            userRepository.save(user);
        }
    }

    private boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty())
            return false;
        return url.matches("^(https?://)?([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(/.*)?$");
    }

}