package iuh.fit.ottbackend.repository;

import iuh.fit.ottbackend.entity.TwoFactorAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TwoFactorAuthRepository extends JpaRepository<TwoFactorAuth, String> {
    Optional<TwoFactorAuth> findByUserId(String userId);
    boolean existsByUserIdAndIsEnabledTrue(String userId);
}