package iuh.fit.authservice.repository;

import iuh.fit.authservice.entity.QrCode;
import iuh.fit.authservice.entity.QrLoginSession;
import iuh.fit.authservice.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QrLoginSessionRepository extends JpaRepository<QrLoginSession, String> {
    Optional<QrLoginSession> findByQrCode(QrCode qrCode);

    @Modifying
    @Query("UPDATE QrLoginSession q SET q.session = null WHERE q.session = :session")
    void nullifySessionReference(@Param("session") UserSession session);
}