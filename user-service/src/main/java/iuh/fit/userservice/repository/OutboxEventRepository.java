package iuh.fit.userservice.repository;

import iuh.fit.userservice.entity.OutboxEvent;
import iuh.fit.userservice.entity.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findTop100ByStatusOrderByIdAsc(OutboxStatus status);
}
