package iuh.fit.se.analyticservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.se.analyticservice.entity.AdminAuditLog;

@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, String> {

    boolean existsByEventId(String eventId);

    long countByActionType(String actionType);

    List<AdminAuditLog> findTop10ByOrderByCreatedAtDesc();
}
