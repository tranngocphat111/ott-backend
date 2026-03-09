package iuh.fit.userservice.repository;

import iuh.fit.userservice.entity.TwoFactorAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TwoFactorAuthRepository extends JpaRepository<TwoFactorAuth, String> {
    Optional<TwoFactorAuth> findByUserId(String userId);
    boolean existsByUserIdAndIsEnabledTrue(String userId);

    @Modifying
    @Query(value = "DELETE FROM two_factor_auth WHERE user_id = :userId", nativeQuery = true)
    void deleteByUserIdNative(@Param("userId") String userId);
}