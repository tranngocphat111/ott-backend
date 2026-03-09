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

    Page<LoginHistory> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    List<LoginHistory> findByUserAndStatus(User user, String status);

    @Query("SELECT lh FROM LoginHistory lh WHERE lh.user = :user AND lh.createdAt BETWEEN :startDate AND :endDate ORDER BY lh.createdAt DESC")
    List<LoginHistory> findByUserAndDateRange(
            @Param("user") User user,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT lh FROM LoginHistory lh WHERE lh.user = :user AND lh.status = 'FAILED' AND lh.createdAt > :since ORDER BY lh.createdAt DESC")
    List<LoginHistory> findRecentFailedAttempts(
            @Param("user") User user,
            @Param("since") LocalDateTime since
    );

    @Query("SELECT COUNT(lh) FROM LoginHistory lh WHERE lh.ipAddress = :ipAddress AND lh.createdAt > :since")
    long countLoginAttemptsByIp(
            @Param("ipAddress") String ipAddress,
            @Param("since") LocalDateTime since
    );

    void deleteByCreatedAtBefore(LocalDateTime dateTime);

    @Query("SELECT lh.deviceType, COUNT(lh) FROM LoginHistory lh WHERE lh.user = :user GROUP BY lh.deviceType")
    List<Object[]> getLoginStatsByDeviceType(@Param("user") User user);
}