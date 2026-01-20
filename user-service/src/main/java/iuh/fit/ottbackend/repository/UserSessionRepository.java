package iuh.fit.ottbackend.repository;

import iuh.fit.ottbackend.entity.User;
import iuh.fit.ottbackend.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, String> {

    List<UserSession> findByUser(User user);

    Optional<UserSession> findBySessionToken(String sessionToken);

    Optional<UserSession> findByDeviceIdAndUser(String deviceId, User user);

    @Query("SELECT s FROM UserSession s WHERE s.user = :user AND s.expiresAt > :now")
    List<UserSession> findActiveSessionsByUser(@Param("user") User user, @Param("now") LocalDateTime now);

    @Query("SELECT s FROM UserSession s WHERE s.expiresAt < :now")
    List<UserSession> findExpiredSessions(@Param("now") LocalDateTime now);

    void deleteByExpiresAtBefore(LocalDateTime dateTime);

    @Query("SELECT COUNT(s) FROM UserSession s WHERE s.user = :user AND s.expiresAt > :now")
    long countActiveSessionsByUser(@Param("user") User user, @Param("now") LocalDateTime now);

    List<UserSession> findByUserAndDeviceType(User user, String deviceType);

    Optional<UserSession> findByDeviceIdAndUserAndIsActive(String deviceId, User user, Boolean isActive);

    List<UserSession> findByUserIdAndIsActiveTrue(String userId);

    List<UserSession> findByUserIdAndIsActiveTrueOrderByLastActiveAtDesc(String userId);

    List<UserSession> findByExpiresAtBeforeAndIsActiveTrue(LocalDateTime expiryTime);

    List<UserSession> findAllByDeviceIdAndUserAndIsActive(String deviceId, User user, boolean isActive);

    Optional<UserSession> findByRefreshToken(String refreshToken);

    Optional<UserSession> findByUserIdAndDeviceIdAndIsActiveTrue(String userId, String deviceId);
}
