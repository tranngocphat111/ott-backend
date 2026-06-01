package iuh.fit.se.analyticservice.repository;

import java.time.Instant;
import java.util.List;

import iuh.fit.se.analyticservice.entity.RawLoginEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RawLoginEventRepository extends JpaRepository<RawLoginEvent, String> {
	long countByTimestampGreaterThanEqual(Instant from);

	long countByTimestampGreaterThanEqualAndTimestampLessThan(Instant from, Instant to);

	List<RawLoginEvent> findByTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(
			Instant from,
			Instant to,
			Pageable pageable);

	long deleteByTimestampGreaterThanEqualAndTimestampLessThan(Instant from, Instant to);

	@Query("SELECT LOWER(l.loginMethod), COUNT(l) FROM RawLoginEvent l GROUP BY LOWER(l.loginMethod)")
	List<Object[]> countByLoginMethod();

	@Query("SELECT LOWER(l.loginMethod), COUNT(l) FROM RawLoginEvent l WHERE l.timestamp >= :from GROUP BY LOWER(l.loginMethod)")
	List<Object[]> countByLoginMethodFrom(@Param("from") Instant from);

	@Query(
			value = """
					SELECT CAST(event_timestamp AS date) AS event_date, COUNT(*) AS event_count
					FROM raw_login_events
					GROUP BY CAST(event_timestamp AS date)
					ORDER BY event_date
					""",
			nativeQuery = true
	)
	List<Object[]> countLoginsByDateAll();

	@Query(
			value = """
					SELECT CAST(event_timestamp AS date) AS event_date, COUNT(*) AS event_count
					FROM raw_login_events
					WHERE event_timestamp >= :from
					GROUP BY CAST(event_timestamp AS date)
					ORDER BY event_date
					""",
			nativeQuery = true
	)
	List<Object[]> countLoginsByDateFrom(@Param("from") Instant from);

	@Query("SELECT COUNT(DISTINCT l.userId) FROM RawLoginEvent l WHERE l.timestamp >= :from")
	long countDistinctUsersFrom(@Param("from") Instant from);

	@Query("SELECT COUNT(DISTINCT l.userId) FROM RawLoginEvent l WHERE l.timestamp >= :from AND l.timestamp < :to")
	long countDistinctUsersBetween(@Param("from") Instant from, @Param("to") Instant to);
}
