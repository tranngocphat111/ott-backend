package iuh.fit.ottbackend.repository;

import iuh.fit.ottbackend.entity.LoginHistory;
import iuh.fit.ottbackend.entity.User;
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

    // Tìm login history theo user (phân trang)
    Page<LoginHistory> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    // Tìm login history theo user và status
    List<LoginHistory> findByUserAndStatus(User user, String status);

    // Tìm login history trong khoảng thời gian
    @Query("SELECT lh FROM LoginHistory lh WHERE lh.user = :user AND lh.createdAt BETWEEN :startDate AND :endDate ORDER BY lh.createdAt DESC")
    List<LoginHistory> findByUserAndDateRange(
            @Param("user") User user,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // Tìm failed login attempts
    @Query("SELECT lh FROM LoginHistory lh WHERE lh.user = :user AND lh.status = 'FAILED' AND lh.createdAt > :since ORDER BY lh.createdAt DESC")
    List<LoginHistory> findRecentFailedAttempts(
            @Param("user") User user,
            @Param("since") LocalDateTime since
    );

    // Đếm login attempts theo IP address
    @Query("SELECT COUNT(lh) FROM LoginHistory lh WHERE lh.ipAddress = :ipAddress AND lh.createdAt > :since")
    long countLoginAttemptsByIp(
            @Param("ipAddress") String ipAddress,
            @Param("since") LocalDateTime since
    );

    // Xóa login history cũ (cleanup)
    void deleteByCreatedAtBefore(LocalDateTime dateTime);

    // Thống kê login theo device type
    @Query("SELECT lh.deviceType, COUNT(lh) FROM LoginHistory lh WHERE lh.user = :user GROUP BY lh.deviceType")
    List<Object[]> getLoginStatsByDeviceType(@Param("user") User user);
}
