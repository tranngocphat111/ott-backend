package iuh.fit.se.analyticservice.repository;

import iuh.fit.se.analyticservice.entity.RawLoginEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RawLoginEventRepository extends JpaRepository<RawLoginEvent, String> {
}
