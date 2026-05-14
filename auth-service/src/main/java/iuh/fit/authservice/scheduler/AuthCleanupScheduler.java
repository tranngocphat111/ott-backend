package iuh.fit.authservice.scheduler;

import iuh.fit.authservice.repository.InvalidatedTokenRepository;
import iuh.fit.authservice.repository.LoginHistoryRepository;
import iuh.fit.authservice.repository.QrCodeRepository;
import iuh.fit.authservice.repository.UserSessionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthCleanupScheduler {

    private final InvalidatedTokenRepository invalidatedTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final QrCodeRepository qrCodeRepository;
    private final LoginHistoryRepository loginHistoryRepository;

    @Scheduled(fixedDelay = 30 * 60 * 1000)
    @Transactional
    public void cleanupExpiredInvalidatedTokens() {
        try {
            var expiredTokens = invalidatedTokenRepository.findByExpiryTimeBefore(LocalDateTime.now());
            if (!expiredTokens.isEmpty()) {
                invalidatedTokenRepository.deleteAll(expiredTokens);
                log.info("Cleaned up {} expired invalidated tokens", expiredTokens.size());
            }
        } catch (Exception e) {
            log.error("Error cleaning up invalidated tokens", e);
        }
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000)
    @Transactional
    public void cleanupExpiredSessions() {
        try {
            var expiredSessions = userSessionRepository.findByExpiresAtBeforeAndIsActiveTrue(LocalDateTime.now());
            if (!expiredSessions.isEmpty()) {
                expiredSessions.forEach(session -> session.revoke("Session expired"));
                userSessionRepository.saveAll(expiredSessions);
                log.info("Marked {} expired sessions as inactive", expiredSessions.size());
            }
        } catch (Exception e) {
            log.error("Error cleaning up expired sessions", e);
        }
    }

    @Scheduled(fixedDelay = 6 * 60 * 60 * 1000)
    @Transactional
    public void cleanupOldQrCodes() {
        try {
            LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
            qrCodeRepository.deleteOrphanQrLoginSessionsBefore(oneDayAgo);
            qrCodeRepository.deleteByCreatedAtBefore(oneDayAgo);
            log.info("Cleaned up QR codes older than 1 day");
        } catch (Exception e) {
            log.error("Error cleaning up old QR codes", e);
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldLoginHistory() {
        try {
            LocalDateTime ninetyDaysAgo = LocalDateTime.now().minusDays(90);
            loginHistoryRepository.deleteByCreatedAtBefore(ninetyDaysAgo);
            log.info("Cleaned up login history older than 90 days");
        } catch (Exception e) {
            log.error("Error cleaning up old login history", e);
        }
    }
}