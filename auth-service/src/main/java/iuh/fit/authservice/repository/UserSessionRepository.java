package iuh.fit.authservice.repository;

import iuh.fit.authservice.entity.User;
import iuh.fit.authservice.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, String> {

    Optional<UserSession> findByDeviceIdAndUserId(String deviceId, String userId);

    Optional<UserSession> findByDeviceIdAndUserIdAndIsActive(String deviceId, String userId, Boolean isActive);

    @Query("SELECT s FROM UserSession s WHERE s.userId = :userId AND s.expiresAt > :now")
    List<UserSession> findActiveSessionsByUserId(@Param("userId") String userId, @Param("now") LocalDateTime now);

    @Query("SELECT s FROM UserSession s WHERE s.expiresAt < :now")
    List<UserSession> findExpiredSessions(@Param("now") LocalDateTime now);

    void deleteByExpiresAtBefore(LocalDateTime dateTime);

    @Query("SELECT COUNT(s) FROM UserSession s WHERE s.userId = :userId AND s.expiresAt > :now")
    long countActiveSessionsByUserId(@Param("userId") String userId, @Param("now") LocalDateTime now);

    List<UserSession> findByUserIdAndIsActiveTrue(String userId);

    List<UserSession> findByUserIdAndIsActiveTrueOrderByLastActiveAtDesc(String userId);

    List<UserSession> findByExpiresAtBeforeAndIsActiveTrue(LocalDateTime expiryTime);

    Optional<UserSession> findByRefreshTokenAndIsActive(String refreshToken, Boolean isActive);
}