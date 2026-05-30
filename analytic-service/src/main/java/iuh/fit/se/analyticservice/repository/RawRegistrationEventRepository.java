package iuh.fit.se.analyticservice.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import iuh.fit.se.analyticservice.entity.RawRegistrationEvent;

public interface RawRegistrationEventRepository extends JpaRepository<RawRegistrationEvent, String> {

    List<RawRegistrationEvent> findTop5ByOrderByTimestampDesc();

    long countByTimestampGreaterThanEqualAndTimestampLessThan(Instant from, Instant to);

    List<RawRegistrationEvent> findByTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(
            Instant from,
            Instant to,
            Pageable pageable);

    long deleteByTimestampGreaterThanEqualAndTimestampLessThan(Instant from, Instant to);
}
