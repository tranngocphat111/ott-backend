package iuh.fit.notificationservice.repository;

import iuh.fit.notificationservice.entity.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {
    Optional<PushToken> findByExpoPushToken(String expoPushToken);

    List<PushToken> findByUserIdAndActiveTrue(String userId);
}
