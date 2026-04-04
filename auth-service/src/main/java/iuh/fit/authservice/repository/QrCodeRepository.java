package iuh.fit.authservice.repository;

import iuh.fit.authservice.entity.QrCode;
import iuh.fit.authservice.entity.User;
import iuh.fit.authservice.entity.enums.QrCodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface QrCodeRepository extends JpaRepository<QrCode, String> {

    List<QrCode> findByUserId(String userId);

    @Query("SELECT q FROM QrCode q WHERE q.expiresAt < :now AND q.status IN ('PENDING', 'SCANNED')")
    List<QrCode> findExpiredQrCodes(@Param("now") LocalDateTime now);

    Optional<QrCode> findByDeviceIdAndStatus(String deviceId, String status);

    void deleteByCreatedAtBefore(LocalDateTime dateTime);

    @Query("SELECT SUM(q.failedAttempts) FROM QrCode q WHERE q.deviceId = :deviceId AND q.createdAt > :since")
    Integer countFailedAttemptsByDevice(@Param("deviceId") String deviceId, @Param("since") LocalDateTime since);

    long countByUserIdAndStatus(String userId, QrCodeStatus status);

    @Modifying
    @Query(value = """
    DELETE FROM qr_login_sessions
    WHERE qr_code_id IN (
        SELECT id FROM qr_codes WHERE created_at < :dateTime
    )
    """, nativeQuery = true)
    void deleteOrphanQrLoginSessionsBefore(@Param("dateTime") LocalDateTime dateTime);
}