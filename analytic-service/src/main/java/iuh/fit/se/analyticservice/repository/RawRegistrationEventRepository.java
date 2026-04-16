package iuh.fit.se.analyticservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import iuh.fit.se.analyticservice.entity.RawRegistrationEvent;

public interface RawRegistrationEventRepository extends JpaRepository<RawRegistrationEvent, String> {
    List<RawRegistrationEvent> findTop5ByOrderByTimestampDesc();
}
