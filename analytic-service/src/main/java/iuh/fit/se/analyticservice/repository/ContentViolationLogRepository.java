package iuh.fit.se.analyticservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import iuh.fit.se.analyticservice.entity.ContentViolationLog;

public interface ContentViolationLogRepository extends JpaRepository<ContentViolationLog, UUID> {

    boolean existsByViolationId(String violationId);
}
