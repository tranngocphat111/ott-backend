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

        @Query("SELECT FUNCTION('DATE', m.timestamp), COUNT(m) FROM RawMessageEvent m " +
            "GROUP BY FUNCTION('DATE', m.timestamp) ORDER BY FUNCTION('DATE', m.timestamp)")
        List<Object[]> countMessagesByDateAll();

        @Query("SELECT FUNCTION('DATE', m.timestamp), COUNT(m) FROM RawMessageEvent m " +
            "WHERE m.timestamp >= :from GROUP BY FUNCTION('DATE', m.timestamp) " +
            "ORDER BY FUNCTION('DATE', m.timestamp)")
        List<Object[]> countMessagesByDateFrom(@Param("from") Instant from);
}
