package iuh.fit.notificationservice.repository;

import iuh.fit.notificationservice.entity.EmailLog;
import iuh.fit.notificationservice.entity.enums.EmailStatus;
import iuh.fit.notificationservice.entity.enums.EmailType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, String> {

    List<EmailLog> findByUserIdOrderByCreatedAtDesc(String userId);

    List<EmailLog> findByStatus(EmailStatus status);

    List<EmailLog> findByStatusAndRetryCountLessThan(EmailStatus status, int maxRetry);

    boolean existsByEmailToAndEmailTypeAndStatus(String emailTo, EmailType emailType, EmailStatus status);

    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}