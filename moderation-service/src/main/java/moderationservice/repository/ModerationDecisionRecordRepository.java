package moderationservice.repository;

import moderationservice.entity.ModerationDecisionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModerationDecisionRecordRepository extends JpaRepository<ModerationDecisionRecord, String> {

    boolean existsByRequestId(String requestId);

    Optional<ModerationDecisionRecord> findByRequestId(String requestId);
}
