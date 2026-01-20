package iuh.fit.ottbackend.scheduler;

import iuh.fit.ottbackend.entity.QrCode;
import iuh.fit.ottbackend.entity.UserSession;
import iuh.fit.ottbackend.entity.enums.QrCodeStatus;
import iuh.fit.ottbackend.repository.LoginHistoryRepository;
import iuh.fit.ottbackend.repository.QrCodeRepository;
import iuh.fit.ottbackend.repository.UserSessionRepository;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class QrCodeCleanupScheduler {

    QrCodeRepository qrCodeRepository;
    UserSessionRepository userSessionRepository;
    LoginHistoryRepository loginHistoryRepository;

    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void cleanupExpiredQrCodes() {
        log.info("Starting QR code cleanup task...");

        try {
            List<QrCode> expiredQrCodes = qrCodeRepository.findExpiredQrCodes(LocalDateTime.now());

            if (!expiredQrCodes.isEmpty()) {
                expiredQrCodes.forEach(qrCode -> {
                    qrCode.setStatus(QrCodeStatus.EXPIRED);
                    qrCode.setUpdatedAt(LocalDateTime.now());
                });

                qrCodeRepository.saveAll(expiredQrCodes);
                log.info("Updated {} expired QR codes", expiredQrCodes.size());
            }

            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);
            qrCodeRepository.deleteByCreatedAtBefore(cutoffTime);
            log.info("Deleted old QR codes created before {}", cutoffTime);

        } catch (Exception e) {
            log.error("Error during QR code cleanup", e);
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredSessions() {
        log.info("Starting expired sessions cleanup task...");

        try {
            List<UserSession> expiredSessions = userSessionRepository.findExpiredSessions(LocalDateTime.now());

            if (!expiredSessions.isEmpty()) {
                userSessionRepository.deleteAll(expiredSessions);
                log.info("Deleted {} expired sessions", expiredSessions.size());
            }

        } catch (Exception e) {
            log.error("Error during session cleanup", e);
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldLoginHistory() {
        log.info("Starting old login history cleanup task...");

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(90);
            loginHistoryRepository.deleteByCreatedAtBefore(cutoffTime);
            log.info("Deleted login history records older than {}", cutoffTime);

        } catch (Exception e) {
            log.error("Error during login history cleanup", e);
        }
    }
}
