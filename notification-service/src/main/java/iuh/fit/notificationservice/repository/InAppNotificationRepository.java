package iuh.fit.notificationservice.repository;

import iuh.fit.notificationservice.entity.InAppNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InAppNotificationRepository extends JpaRepository<InAppNotification, Long> {
    List<InAppNotification> findByRecipientIdOrderByCreatedAtDesc(String recipientId);
    List<InAppNotification> findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(String recipientId);
}
