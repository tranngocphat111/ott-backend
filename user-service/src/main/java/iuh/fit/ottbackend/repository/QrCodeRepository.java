package iuh.fit.ottbackend.repository;

import iuh.fit.ottbackend.entity.QrCode;
import iuh.fit.ottbackend.entity.User;
import iuh.fit.ottbackend.entity.enums.QrCodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface QrCodeRepository extends JpaRepository<QrCode, String> {

    // Tìm QR codes theo user
    List<QrCode> findByUser(User user);

    // Tìm QR codes theo user và type
    List<QrCode> findByUserAndQrType(User user, String qrType);

    // Tìm QR codes theo status
    List<QrCode> findByStatus(String status);

    // Tìm QR codes đã hết hạn
    @Query("SELECT q FROM QrCode q WHERE q.expiresAt < :now AND q.status IN ('PENDING', 'SCANNED')")
    List<QrCode> findExpiredQrCodes(@Param("now") LocalDateTime now);

    // Tìm QR code theo device ID
    Optional<QrCode> findByDeviceIdAndStatus(String deviceId, String status);

    // Xóa QR codes cũ (cleanup)
    void deleteByCreatedAtBefore(LocalDateTime dateTime);

    // Đếm số lượng failed attempts theo device
    @Query("SELECT SUM(q.failedAttempts) FROM QrCode q WHERE q.deviceId = :deviceId AND q.createdAt > :since")
    Integer countFailedAttemptsByDevice(@Param("deviceId") String deviceId, @Param("since") LocalDateTime since);

    long countByUserAndStatus(User user, QrCodeStatus status);
}
