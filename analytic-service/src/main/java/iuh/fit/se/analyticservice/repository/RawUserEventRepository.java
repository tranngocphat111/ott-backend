package iuh.fit.se.analyticservice.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import iuh.fit.se.analyticservice.entity.RawUserEvent;

public interface RawUserEventRepository extends JpaRepository<RawUserEvent, String> {
    List<RawUserEvent> findAllByOrderByTimestampDesc();

    Page<RawUserEvent> findAllByOrderByTimestampDesc(Pageable pageable);

    List<RawUserEvent> findAllByTimestampGreaterThanEqualOrderByTimestampDesc(Instant from);

    Page<RawUserEvent> findAllByTimestampGreaterThanEqualOrderByTimestampDesc(Instant from, Pageable pageable);

    List<RawUserEvent> findTop5ByOrderByTimestampDesc();

    List<RawUserEvent> findTop5ByTimestampGreaterThanEqualOrderByTimestampDesc(Instant from);

    long countByTimestampGreaterThanEqual(Instant from);

    long countByTimestampGreaterThanEqualAndTimestampLessThan(Instant from, Instant to);

    List<RawUserEvent> findByTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(
            Instant from,
            Instant to,
            Pageable pageable);

    long deleteByTimestampGreaterThanEqualAndTimestampLessThan(Instant from, Instant to);

    @Query(
            value = """
                    SELECT CAST(event_timestamp AS date) AS event_date, COUNT(*) AS event_count
                    FROM raw_user_events
                    GROUP BY CAST(event_timestamp AS date)
                    ORDER BY event_date
                    """,
            nativeQuery = true
    )
    List<Object[]> countRegistrationsByDateAll();

    @Query(
            value = """
                    SELECT CAST(event_timestamp AS date) AS event_date, COUNT(*) AS event_count
                    FROM raw_user_events
                    WHERE event_timestamp >= :from
                    GROUP BY CAST(event_timestamp AS date)
                    ORDER BY event_date
                    """,
            nativeQuery = true
    )
    List<Object[]> countRegistrationsByDateFrom(@Param("from") Instant from);
}
