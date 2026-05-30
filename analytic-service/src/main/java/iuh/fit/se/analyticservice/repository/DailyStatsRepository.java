package iuh.fit.se.analyticservice.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import iuh.fit.se.analyticservice.entity.DailyStats;

public interface DailyStatsRepository extends JpaRepository<DailyStats, UUID> {

    Optional<DailyStats> findByStatDate(LocalDate statDate);

    List<DailyStats> findByStatDateBetweenOrderByStatDateAsc(LocalDate from, LocalDate to);
}
