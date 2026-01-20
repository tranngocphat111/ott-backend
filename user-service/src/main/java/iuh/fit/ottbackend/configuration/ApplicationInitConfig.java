package iuh.fit.ottbackend.configuration;

import iuh.fit.ottbackend.entity.User;
import iuh.fit.ottbackend.entity.enums.AccountType;
import iuh.fit.ottbackend.entity.enums.Gender;
import iuh.fit.ottbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ApplicationInitConfig {

    private final PasswordEncoder passwordEncoder;

    @Bean
    ApplicationRunner applicationRunner(UserRepository userRepository) {
        return args -> {

            if (userRepository.findByPhone("0912345678").isEmpty()) {
                User testUser = User.builder()
                        .phone("0912345678")
                        .email("testuser@example.com")
                        .passwordHash(passwordEncoder.encode("password123"))
                        .fullName("Test User")
                        .dateOfBirth(LocalDate.of(1990, 1, 1))
                        .gender(Gender.MALE)
                        .accountType(AccountType.USER)
                        .isPhoneVerified(true)
                        .phoneVerifiedAt(LocalDateTime.now())
                        .isEmailVerified(true)
                        .emailVerifiedAt(LocalDateTime.now())
                        .isActive(true)
                        .isBlocked(false)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

                userRepository.save(testUser);
            }

            if (userRepository.findByPhone("0987654321").isEmpty()) {
                User adminUser = User.builder()
                        .phone("0987654321")
                        .email("admin@ottbackend.com")
                        .passwordHash(passwordEncoder.encode("admin123"))
                        .fullName("Admin User")
                        .dateOfBirth(LocalDate.of(1985, 1, 1))
                        .gender(Gender.MALE)
                        .accountType(AccountType.ADMIN)
                        .isPhoneVerified(true)
                        .phoneVerifiedAt(LocalDateTime.now())
                        .isEmailVerified(true)
                        .emailVerifiedAt(LocalDateTime.now())
                        .isActive(true)
                        .isBlocked(false)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

                userRepository.save(adminUser);
            }

            createTestUsers(userRepository);

        };
    }

    private void createTestUsers(UserRepository userRepository) {
        // User 2 - Blocked user
        if (userRepository.findByPhone("0911111111").isEmpty()) {
            User blockedUser = User.builder()
                    .phone("0911111111")
                    .email("blocked@example.com")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .fullName("Blocked User")
                    .accountType(AccountType.USER)
                    .isPhoneVerified(true)
                    .phoneVerifiedAt(LocalDateTime.now())
                    .isActive(true)
                    .isBlocked(true)
                    .blockedUntil(LocalDateTime.now().plusDays(7))
                    .blockedReason("Test blocked account")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(blockedUser);
        }

        if (userRepository.findByPhone("0922222222").isEmpty()) {
            User inactiveUser = User.builder()
                    .phone("0922222222")
                    .email("inactive@example.com")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .fullName("Inactive User")
                    .accountType(AccountType.USER)
                    .isPhoneVerified(true)
                    .phoneVerifiedAt(LocalDateTime.now())
                    .isActive(false)
                    .isBlocked(false)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(inactiveUser);
        }

        if (userRepository.findByPhone("0933333333").isEmpty()) {
            User googleUser = User.builder()
                    .phone("0933333333")
                    .email("google@example.com")
                    .googleId("google_test_id_123456")
                    .passwordHash(passwordEncoder.encode("password123"))
                    .fullName("Google User")
                    .avatarUrl("https://lh3.googleusercontent.com/test-avatar")
                    .accountType(AccountType.USER)
                    .isPhoneVerified(true)
                    .phoneVerifiedAt(LocalDateTime.now())
                    .isEmailVerified(true)
                    .emailVerifiedAt(LocalDateTime.now())
                    .isActive(true)
                    .isBlocked(false)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(googleUser);
        }

    }
}