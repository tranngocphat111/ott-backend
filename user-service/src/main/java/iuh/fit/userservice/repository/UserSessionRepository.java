package iuh.fit.userservice.repository;

import iuh.fit.userservice.entity.User;
import iuh.fit.userservice.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, String> {

    Optional<UserSession> findByDeviceIdAndUser(String deviceId, User user);
    Optional<UserSession> findByDeviceIdAndUserAndIsActive(String deviceId, User user, Boolean isActive);

    List<UserSession> findByUserIdAndIsActiveTrue(String userId);
    List<UserSession> findByUserIdAndIsActiveTrueOrderByLastActiveAtDesc(String userId);
    List<UserSession> findByExpiresAtBeforeAndIsActiveTrue(LocalDateTime expiryTime);

    @Query("SELECT s FROM UserSession s WHERE s.user = :user AND s.expiresAt > :now")
    List<UserSession> findActiveSessionsByUser(@Param("user") User user, @Param("now") LocalDateTime now);
}