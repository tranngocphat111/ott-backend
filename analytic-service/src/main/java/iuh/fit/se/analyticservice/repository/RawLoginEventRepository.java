package iuh.fit.se.analyticservice.repository;

import java.time.Instant;
import java.util.List;

import iuh.fit.se.analyticservice.entity.RawLoginEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RawLoginEventRepository extends JpaRepository<RawLoginEvent, String> {
	long countByTimestampGreaterThanEqual(Instant from);

	@Query("SELECT LOWER(l.loginMethod), COUNT(l) FROM RawLoginEvent l GROUP BY LOWER(l.loginMethod)")
	List<Object[]> countByLoginMethod();

	@Query("SELECT LOWER(l.loginMethod), COUNT(l) FROM RawLoginEvent l WHERE l.timestamp >= :from GROUP BY LOWER(l.loginMethod)")
	List<Object[]> countByLoginMethodFrom(@Param("from") Instant from);

	@Query("SELECT FUNCTION('DATE', l.timestamp), COUNT(l) FROM RawLoginEvent l GROUP BY FUNCTION('DATE', l.timestamp) ORDER BY FUNCTION('DATE', l.timestamp)")
	List<Object[]> countLoginsByDateAll();

	@Query("SELECT FUNCTION('DATE', l.timestamp), COUNT(l) FROM RawLoginEvent l WHERE l.timestamp >= :from GROUP BY FUNCTION('DATE', l.timestamp) ORDER BY FUNCTION('DATE', l.timestamp)")
	List<Object[]> countLoginsByDateFrom(@Param("from") Instant from);

	@Query("SELECT COUNT(DISTINCT l.userId) FROM RawLoginEvent l WHERE l.timestamp >= :from")
	long countDistinctUsersFrom(@Param("from") Instant from);
}
