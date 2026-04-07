package iuh.fit.authservice.service;

import iuh.fit.authservice.entity.User;
import iuh.fit.authservice.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSyncService {

    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureUserExists(UserServiceClient.UserDto userDto) {
        log.debug("Starting user sync check for userId: {}", userDto.getId());

        try {
            // Check cả id lẫn phone để tránh duplicate
            boolean exists = userRepository.existsById(userDto.getId())
                    || userRepository.existsByPhone(userDto.getPhone());

            if (!exists) {
                log.info("User not found in auth DB, syncing new user - userId: {}", userDto.getId());

                User user = User.builder()
                        .id(userDto.getId())
                        .phone(userDto.getPhone())
                        .email(userDto.getEmail() != null ? userDto.getEmail() : "")
                        .googleId(userDto.getGoogleId())
                        .fullName(userDto.getFullName() != null ? userDto.getFullName() : "")
                        .avatarUrl(userDto.getAvatarUrl())
                        .isActive(Boolean.TRUE.equals(userDto.getIsActive()))
                        .isBlocked(Boolean.TRUE.equals(userDto.getIsBlocked()))
                        .isFirstLogin(Boolean.TRUE.equals(userDto.getIsFirstLogin()))
                        .welcomeEmailSent(Boolean.TRUE.equals(userDto.getWelcomeEmailSent()))
                        .build();

                userRepository.saveAndFlush(user);
                log.info("Successfully synced new user to auth-service DB - userId: {}", userDto.getId());
            } else {
                log.debug("User already exists in auth DB, skipping sync - userId: {}", userDto.getId());
            }
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.debug("Concurrent insert detected for userId: {} (expected in high concurrency), skipping",
                    userDto.getId());
        } catch (Exception e) {
            log.error("Failed to sync user to auth DB - userId: {}", userDto.getId(), e);
        }
    }
}