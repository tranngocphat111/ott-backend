package iuh.fit.se.analyticservice.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import iuh.fit.se.analyticservice.config.DailyStatsProperties;
import iuh.fit.se.analyticservice.entity.DailyStats;
import iuh.fit.se.analyticservice.repository.ContentViolationLogRepository;
import iuh.fit.se.analyticservice.repository.DailyStatsRepository;
import iuh.fit.se.analyticservice.repository.RawLoginEventRepository;
import iuh.fit.se.analyticservice.repository.RawMessageEventRepository;
import iuh.fit.se.analyticservice.repository.RawPostEventRepository;
import iuh.fit.se.analyticservice.repository.RawUserEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyStatsAggregationService {

    private final DailyStatsProperties properties;
    private final DailyStatsRepository dailyStatsRepository;
    private final RawUserEventRepository rawUserEventRepository;
    private final RawLoginEventRepository rawLoginEventRepository;
    private final RawMessageEventRepository rawMessageEventRepository;
    private final RawPostEventRepository rawPostEventRepository;
    private final ContentViolationLogRepository contentViolationLogRepository;

    @Scheduled(cron = "${analytics.daily-stats.cron}", zone = "${analytics.daily-stats.zone}")
    public void aggregateRecentWindowScheduled() {
        if (!properties.isEnabled()) {
            log.debug("Daily stats aggregation is disabled");
            return;
        }

        aggregateRecentWindow();
    }

    @Transactional
    public void aggregateRecentWindow() {
        int lookbackDays = Math.max(0, properties.getLookbackDays());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        for (int offset = lookbackDays; offset >= 0; offset--) {
            aggregateDate(today.minusDays(offset));
        }
    }

    @Transactional
    public DailyStats aggregateDate(LocalDate statDate) {
        if (statDate == null) {
            throw new IllegalArgumentException("statDate must not be null");
        }

        Instant start = statDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = statDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        LocalDateTime localStart = LocalDateTime.ofInstant(start, ZoneOffset.UTC);
        LocalDateTime localEnd = LocalDateTime.ofInstant(end, ZoneOffset.UTC);

        DailyStats stats = dailyStatsRepository.findByStatDate(statDate)
                .orElseGet(() -> DailyStats.builder().statDate(statDate).build());

        stats.setRegisteredUsers(rawUserEventRepository.countByTimestampGreaterThanEqualAndTimestampLessThan(start, end));
        stats.setLoginEvents(rawLoginEventRepository.countByTimestampGreaterThanEqualAndTimestampLessThan(start, end));
        stats.setActiveUsers(rawLoginEventRepository.countDistinctUsersBetween(start, end));
        stats.setMessageEvents(rawMessageEventRepository.countByTimestampGreaterThanEqualAndTimestampLessThan(start, end));
        stats.setPostEvents(rawPostEventRepository.countByTimestampGreaterThanEqualAndTimestampLessThan(start, end));
        stats.setViolationEvents(contentViolationLogRepository.countByDetectedAtGreaterThanEqualAndDetectedAtLessThan(localStart, localEnd));
        stats.setAggregatedAt(Instant.now());

        DailyStats saved = dailyStatsRepository.save(stats);
        log.info("Aggregated daily stats for date={}", statDate);
        return saved;
    }
}
