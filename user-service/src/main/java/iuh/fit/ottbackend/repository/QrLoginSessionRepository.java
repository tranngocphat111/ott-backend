package iuh.fit.ottbackend.repository;

import iuh.fit.ottbackend.entity.QrCode;
import iuh.fit.ottbackend.entity.QrLoginSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QrLoginSessionRepository extends JpaRepository<QrLoginSession, String> {
    Optional<QrLoginSession> findByQrCode(QrCode qrCode);
}
