package iuh.fit.se.analyticservice.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import iuh.fit.se.analyticservice.entity.RawUserEvent;

public interface RawUserEventRepository extends JpaRepository<RawUserEvent, String> {
    List<RawUserEvent> findAllByOrderByTimestampDesc();

    List<RawUserEvent> findAllByTimestampGreaterThanEqualOrderByTimestampDesc(Instant from);

    List<RawUserEvent> findTop5ByOrderByTimestampDesc();

    List<RawUserEvent> findTop5ByTimestampGreaterThanEqualOrderByTimestampDesc(Instant from);

    long countByTimestampGreaterThanEqual(Instant from);

    @org.springframework.data.jpa.repository.Query("SELECT FUNCTION('DATE', r.timestamp), COUNT(r) FROM RawUserEvent r GROUP BY FUNCTION('DATE', r.timestamp) ORDER BY FUNCTION('DATE', r.timestamp)")
    List<Object[]> countRegistrationsByDateAll();

    @org.springframework.data.jpa.repository.Query("SELECT FUNCTION('DATE', r.timestamp), COUNT(r) FROM RawUserEvent r WHERE r.timestamp >= :from GROUP BY FUNCTION('DATE', r.timestamp) ORDER BY FUNCTION('DATE', r.timestamp)")
    List<Object[]> countRegistrationsByDateFrom(@org.springframework.data.repository.query.Param("from") Instant from);
}
