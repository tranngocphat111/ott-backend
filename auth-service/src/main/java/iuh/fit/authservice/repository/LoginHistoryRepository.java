package iuh.fit.authservice.repository;

import iuh.fit.authservice.entity.LoginHistory;
import iuh.fit.authservice.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, String> {

    Page<LoginHistory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    @Query("SELECT lh FROM LoginHistory lh WHERE lh.userId = :userId AND lh.createdAt BETWEEN :startDate AND :endDate ORDER BY lh.createdAt DESC")
    List<LoginHistory> findByUserIdAndDateRange(
            @Param("userId") String userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT lh FROM LoginHistory lh WHERE lh.userId = :userId AND lh.status = 'FAILED' AND lh.createdAt > :since ORDER BY lh.createdAt DESC")
    List<LoginHistory> findRecentFailedAttempts(
            @Param("userId") String userId,
            @Param("since") LocalDateTime since
    );

    @Query("SELECT COUNT(lh) FROM LoginHistory lh WHERE lh.ipAddress = :ipAddress AND lh.createdAt > :since")
    long countLoginAttemptsByIp(
            @Param("ipAddress") String ipAddress,
            @Param("since") LocalDateTime since
    );

    void deleteByCreatedAtBefore(LocalDateTime dateTime);

    @Query("SELECT lh.deviceType, COUNT(lh) FROM LoginHistory lh WHERE lh.userId = :userId GROUP BY lh.deviceType")
    List<Object[]> getLoginStatsByDeviceType(@Param("userId") String userId);
}