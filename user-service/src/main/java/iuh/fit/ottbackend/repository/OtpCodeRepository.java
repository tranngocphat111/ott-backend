package iuh.fit.ottbackend.repository;

import iuh.fit.ottbackend.entity.OtpCode;
import iuh.fit.ottbackend.entity.enums.OtpType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, String> {

    List<OtpCode> findByPhoneAndTypeAndIsUsedFalse(String phone, OtpType type);
    List<OtpCode> findByEmailAndTypeAndIsUsedFalse(String email, OtpType type);


    @Query("SELECT COUNT(o) FROM OtpCode o WHERE o.email = ?1 AND o.type = ?2 " +
            "AND o.createdAt > ?3")
    long countRecentOtpByEmail(String email, OtpType type, LocalDateTime since);

    @Query("SELECT COUNT(o) FROM OtpCode o WHERE o.phone = ?1 AND o.type = ?2 " +
            "AND o.createdAt > ?3")
    long countRecentOtpByPhone(String phone, OtpType type, LocalDateTime since);

    List<OtpCode> findByExpiresAtBeforeAndIsUsedFalse(LocalDateTime dateTime);

    List<OtpCode> findByPhoneAndTypeAndIsUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            String phone, OtpType type, LocalDateTime now);

    List<OtpCode> findByEmailAndTypeAndIsUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            String email, OtpType type, LocalDateTime now);
}
