package iuh.fit.ottbackend.scheduler;

import iuh.fit.ottbackend.entity.InvalidatedToken;
import iuh.fit.ottbackend.entity.UserSession;
import iuh.fit.ottbackend.repository.InvalidatedTokenRepository;
import iuh.fit.ottbackend.repository.UserSessionRepository;
import iuh.fit.ottbackend.service.OtpService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ScheduledTasks {

    OtpService otpService;
    UserSessionRepository userSessionRepository;
    InvalidatedTokenRepository invalidatedTokenRepository;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredOtps() {
        log.info("Starting cleanup of expired OTPs");
        otpService.cleanupExpiredOtps();
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredSessions() {
        log.info("Starting cleanup of expired sessions");

        LocalDateTime now = LocalDateTime.now();
        List<UserSession> expiredSessions = userSessionRepository
                .findByExpiresAtBeforeAndIsActiveTrue(now);

        if (!expiredSessions.isEmpty()) {
            expiredSessions.forEach(session -> {
                session.setIsActive(false);
                session.setRevokedAt(now);
                session.setRevokedReason("Session expired");
            });

            userSessionRepository.saveAll(expiredSessions);
            log.info("Cleaned up {} expired sessions", expiredSessions.size());
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredInvalidatedTokens() {
        log.info("Starting cleanup of expired invalidated tokens");

        LocalDateTime now = LocalDateTime.now();
        List<InvalidatedToken> expiredTokens = invalidatedTokenRepository
                .findByExpiryTimeBefore(now);

        if (!expiredTokens.isEmpty()) {
            invalidatedTokenRepository.deleteAll(expiredTokens);
            log.info("Cleaned up {} expired invalidated tokens", expiredTokens.size());
        }
    }
}