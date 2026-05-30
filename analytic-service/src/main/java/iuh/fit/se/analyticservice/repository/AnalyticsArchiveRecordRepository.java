package iuh.fit.se.analyticservice.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import iuh.fit.se.analyticservice.entity.AnalyticsArchiveRecord;
import iuh.fit.se.analyticservice.enums.ArchiveStatus;

public interface AnalyticsArchiveRecordRepository extends JpaRepository<AnalyticsArchiveRecord, UUID> {

    Optional<AnalyticsArchiveRecord> findByEventTypeAndArchiveDate(String eventType, LocalDate archiveDate);

    List<AnalyticsArchiveRecord> findByStatus(ArchiveStatus status);
}
