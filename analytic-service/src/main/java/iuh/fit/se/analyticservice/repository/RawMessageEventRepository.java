package iuh.fit.se.analyticservice.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import iuh.fit.se.analyticservice.entity.RawMessageEvent;

public interface RawMessageEventRepository extends JpaRepository<RawMessageEvent, String> {

    @Query("SELECT LOWER(m.messageType), COUNT(m) FROM RawMessageEvent m GROUP BY LOWER(m.messageType)")
    List<Object[]> countByMessageType();

        @Query("SELECT LOWER(m.messageType), COUNT(m) FROM RawMessageEvent m " +
            "WHERE m.timestamp >= :from GROUP BY LOWER(m.messageType)")
        List<Object[]> countByMessageTypeFrom(@Param("from") Instant from);

        long countByTimestampGreaterThanEqual(Instant from);

        long countByTimestampGreaterThanEqualAndTimestampLessThan(Instant from, Instant to);

        List<RawMessageEvent> findByTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(
            Instant from,
            Instant to,
            Pageable pageable);

        long deleteByTimestampGreaterThanEqualAndTimestampLessThan(Instant from, Instant to);

        @Query(
            value = """
                    SELECT CAST(event_timestamp AS date) AS event_date, COUNT(*) AS event_count
                    FROM raw_message_events
                    GROUP BY CAST(event_timestamp AS date)
                    ORDER BY event_date
                    """,
            nativeQuery = true
        )
        List<Object[]> countMessagesByDateAll();

        @Query(
            value = """
                    SELECT CAST(event_timestamp AS date) AS event_date, COUNT(*) AS event_count
                    FROM raw_message_events
                    WHERE event_timestamp >= :from
                    GROUP BY CAST(event_timestamp AS date)
                    ORDER BY event_date
                    """,
            nativeQuery = true
        )
        List<Object[]> countMessagesByDateFrom(@Param("from") Instant from);
}
