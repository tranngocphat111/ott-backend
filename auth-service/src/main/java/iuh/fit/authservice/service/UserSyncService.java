package iuh.fit.authservice.service;

import iuh.fit.authservice.entity.User;
import iuh.fit.authservice.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSyncService {

    private final UserRepository userRepository;

    public void ensureUserExists(UserServiceClient.UserDto userDto) {
        try {
            if (userRepository.existsById(userDto.getId())) {
                log.debug("User already exists in auth DB - userId: {}", userDto.getId());
                return;
            }

            // Handle stale auth rows (old deleted accounts) still holding unique googleId.
            if (userDto.getGoogleId() != null && !userDto.getGoogleId().isBlank()) {
                userRepository.findByGoogleId(userDto.getGoogleId())
                        .filter(conflict -> !conflict.getId().equals(userDto.getId()))
                        .ifPresent(conflict -> {
                            conflict.setGoogleId(conflict.getGoogleId() + "_deleted_" + System.currentTimeMillis());
                            conflict.setDeletedAt(LocalDateTime.now());
                            conflict.setIsActive(false);
                            userRepository.saveAndFlush(conflict);
                            log.info("Released conflicting googleId from stale auth user: {}", conflict.getId());
                        });
            }

            User user = User.builder()
                    .id(userDto.getId())
                    .googleId(userDto.getGoogleId())
                    .fullName(userDto.getFullName() != null ? userDto.getFullName() : "")
                    .avatarUrl(userDto.getAvatarUrl())
                    .isActive(Boolean.TRUE.equals(userDto.getIsActive()))
                    .isBlocked(Boolean.TRUE.equals(userDto.getIsBlocked()))
                    .isFirstLogin(Boolean.TRUE.equals(userDto.getIsFirstLogin()))
                    .welcomeEmailSent(Boolean.TRUE.equals(userDto.getWelcomeEmailSent()))
                    .coverUrl(userDto.getCoverUrl())
                    .build();

            userRepository.saveAndFlush(user);
            log.info("User synced to auth DB - userId: {}", userDto.getId());

        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.debug("Concurrent insert for userId: {} - skipping", userDto.getId());
        } catch (Exception e) {
            log.error("Failed to sync user - userId: {}", userDto.getId(), e);
        }
    }
}