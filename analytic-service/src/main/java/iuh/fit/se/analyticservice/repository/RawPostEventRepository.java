package iuh.fit.se.analyticservice.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import iuh.fit.se.analyticservice.entity.RawPostEvent;

public interface RawPostEventRepository extends JpaRepository<RawPostEvent, String> {

    @Query("SELECT FUNCTION('DATE', p.timestamp), COUNT(p) " +
            "FROM RawPostEvent p " +
            "WHERE p.timestamp >= :from " +
            "GROUP BY FUNCTION('DATE', p.timestamp) " +
            "ORDER BY FUNCTION('DATE', p.timestamp)")
    List<Object[]> countPostsByDateFrom(@Param("from") Instant from);

        @Query("SELECT FUNCTION('DATE', p.timestamp), COUNT(p) " +
            "FROM RawPostEvent p " +
            "GROUP BY FUNCTION('DATE', p.timestamp) " +
            "ORDER BY FUNCTION('DATE', p.timestamp)")
        List<Object[]> countPostsByDateAll();

        long countByTimestampGreaterThanEqual(Instant from);
}
