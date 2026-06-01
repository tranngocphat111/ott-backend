package iuh.fit.se.analyticservice.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import iuh.fit.se.analyticservice.entity.RawPostEvent;

public interface RawPostEventRepository extends JpaRepository<RawPostEvent, String> {

    @Query(
            value = """
                    SELECT CAST(event_timestamp AS date) AS event_date, COUNT(*) AS event_count
                    FROM raw_post_events
                    WHERE event_timestamp >= :from
                    GROUP BY CAST(event_timestamp AS date)
                    ORDER BY event_date
                    """,
            nativeQuery = true
    )
    List<Object[]> countPostsByDateFrom(@Param("from") Instant from);

        @Query(
            value = """
                    SELECT CAST(event_timestamp AS date) AS event_date, COUNT(*) AS event_count
                    FROM raw_post_events
                    GROUP BY CAST(event_timestamp AS date)
                    ORDER BY event_date
                    """,
            nativeQuery = true
        )
        List<Object[]> countPostsByDateAll();

        long countByTimestampGreaterThanEqual(Instant from);

        long countByTimestampGreaterThanEqualAndTimestampLessThan(Instant from, Instant to);

        List<RawPostEvent> findByTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(
            Instant from,
            Instant to,
            Pageable pageable);

        long deleteByTimestampGreaterThanEqualAndTimestampLessThan(Instant from, Instant to);
}
