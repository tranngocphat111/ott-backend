package iuh.fit.se.analyticservice.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import iuh.fit.se.analyticservice.entity.RawUserEvent;

public interface RawUserEventRepository extends JpaRepository<RawUserEvent, String> {
    List<RawUserEvent> findTop5ByOrderByTimestampDesc();

    List<RawUserEvent> findTop5ByTimestampGreaterThanEqualOrderByTimestampDesc(Instant from);

    long countByTimestampGreaterThanEqual(Instant from);
}
