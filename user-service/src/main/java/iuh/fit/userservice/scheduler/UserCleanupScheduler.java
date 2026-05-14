package iuh.fit.userservice.scheduler;

import iuh.fit.userservice.repository.UserSessionRepository;
import iuh.fit.userservice.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserCleanupScheduler {

    private final OtpService otpService;
    private final UserSessionRepository userSessionRepository;

    // Dọn OTP expired mỗi 30 phút
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    @Transactional
    public void cleanupExpiredOtps() {
        log.debug("Running OTP cleanup...");
        otpService.cleanupExpiredOtps();
    }

    // Dọn sessions expired mỗi 1 giờ
    @Scheduled(fixedDelay = 60 * 60 * 1000)
    @Transactional
    public void cleanupExpiredSessions() {
        log.debug("Running session cleanup...");
        List<?> expired = userSessionRepository.findByExpiresAtBeforeAndIsActiveTrue(LocalDateTime.now());
        if (!expired.isEmpty()) {
            expired.forEach(s -> {
                var session = (iuh.fit.userservice.entity.UserSession) s;
                session.revoke("Expired");
            });
            userSessionRepository.saveAll((List<iuh.fit.userservice.entity.UserSession>) expired);
            log.info("Cleaned up {} expired sessions", expired.size());
        }
    }
}