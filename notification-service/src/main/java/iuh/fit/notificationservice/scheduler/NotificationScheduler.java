package iuh.fit.notificationservice.scheduler;

import iuh.fit.notificationservice.entity.enums.EmailStatus;
import iuh.fit.notificationservice.repository.EmailLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final EmailLogRepository emailLogRepository;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOldEmailLogs() {
        log.info("Starting cleanup of old email logs...");
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
            emailLogRepository.deleteByCreatedAtBefore(cutoff);
            log.info("Deleted email logs older than {}", cutoff);
        } catch (Exception e) {
            log.error("Error during email log cleanup: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 */30 * * * *")
    public void checkFailedEmails() {
        try {
            var failedLogs = emailLogRepository.findByStatusAndRetryCountLessThan(
                    EmailStatus.FAILED, 3
            );

            if (!failedLogs.isEmpty()) {
                log.warn("Found {} failed emails that need attention", failedLogs.size());
            }
        } catch (Exception e) {
            log.error("Error checking failed emails: {}", e.getMessage());
        }
    }
}