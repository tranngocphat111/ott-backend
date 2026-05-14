package iuh.fit.se.analyticservice.service;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import iuh.fit.se.analyticservice.client.UserServiceClient;
import iuh.fit.se.analyticservice.dto.DailyActivityResponse;
import iuh.fit.se.analyticservice.dto.DailyPostCountResponse;
import iuh.fit.se.analyticservice.dto.MessageTypesResponse;
import iuh.fit.se.analyticservice.dto.OverviewResponse;
import iuh.fit.se.analyticservice.dto.RecentNewUserDTO;
import iuh.fit.se.analyticservice.dto.UserDetailDTO;
import iuh.fit.se.analyticservice.entity.RawUserEvent;
import iuh.fit.se.analyticservice.repository.RawMessageEventRepository;
import iuh.fit.se.analyticservice.repository.RawPostEventRepository;
import iuh.fit.se.analyticservice.repository.RawUserEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAnalyticsService {

    private final RawUserEventRepository rawUserEventRepository;
    private final RawMessageEventRepository rawMessageEventRepository;
    private final RawPostEventRepository rawPostEventRepository;
    private final UserServiceClient userServiceClient;

    public OverviewResponse getOverview(String timeRange) {
        Instant from = resolveFrom(timeRange);
        long totalUsers = from == null ? rawUserEventRepository.count() : rawUserEventRepository.countByTimestampGreaterThanEqual(from);
        long totalMessages = from == null ? rawMessageEventRepository.count() : rawMessageEventRepository.countByTimestampGreaterThanEqual(from);
        long totalPosts = from == null ? rawPostEventRepository.count() : rawPostEventRepository.countByTimestampGreaterThanEqual(from);
        return new OverviewResponse(totalUsers, totalMessages, totalPosts);
    }

    public List<RecentNewUserDTO> getRecentUsers(String timeRange) {
        Instant from = resolveFrom(timeRange);
        List<RawUserEvent> recentEvents = from == null
                ? rawUserEventRepository.findTop5ByOrderByTimestampDesc()
                : rawUserEventRepository.findTop5ByTimestampGreaterThanEqualOrderByTimestampDesc(from);
        List<RecentNewUserDTO> result = new ArrayList<>();

        try {
            for (RawUserEvent event : recentEvents) {
                UserDetailDTO user = userServiceClient.getUserById(event.getUserId());
                result.add(new RecentNewUserDTO(
                        event.getUserId(),
                        user != null ? user.getEmail() : null,
                        user != null ? user.getFullName() : null
                ));
            }
            return result;
        } catch (Exception ex) {
            log.warn("user-service unavailable, return empty recent users list", ex);
            return new ArrayList<>();
        }
    }

    public MessageTypesResponse getMessageTypes(String timeRange) {
        Instant from = resolveFrom(timeRange);
        long text = 0;
        long image = 0;
        long voice = 0;

        List<Object[]> rows = from == null
                ? rawMessageEventRepository.countByMessageType()
                : rawMessageEventRepository.countByMessageTypeFrom(from);
        for (Object[] row : rows) {
            String type = row[0] != null ? row[0].toString().toLowerCase(Locale.ROOT) : "";
            long count = ((Number) row[1]).longValue();

            switch (type) {
                case "text" -> text = count;
                case "image" -> image = count;
                case "voice" -> voice = count;
                default -> {
                    // ignore unknown type to keep API simple for demo
                }
            }
        }

        return new MessageTypesResponse(text, image, voice);
    }

    public List<DailyActivityResponse> getDailyActivity(String timeRange) {
        Instant from = resolveFrom(timeRange);
        Map<LocalDate, Long> postsByDate = countPostsByDate(from);
        Map<LocalDate, Long> messagesByDate = countMessagesByDate(from);

        List<DailyActivityResponse> result = new ArrayList<>();
        for (LocalDate date : buildDateRange(from, postsByDate.keySet(), messagesByDate.keySet())) {
            result.add(new DailyActivityResponse(
                    date,
                    postsByDate.getOrDefault(date, 0L),
                    messagesByDate.getOrDefault(date, 0L)
            ));
        }
        return result;
    }

    public List<DailyPostCountResponse> getPostDailyOnly(String timeRange) {
        Instant from = resolveFrom(timeRange);
        Map<LocalDate, Long> postsByDate = countPostsByDate(from);

        List<DailyPostCountResponse> result = new ArrayList<>();
        for (LocalDate date : buildDateRange(from, postsByDate.keySet(), java.util.Collections.emptySet())) {
            result.add(new DailyPostCountResponse(date, postsByDate.getOrDefault(date, 0L)));
        }
        return result;
    }

    private Map<LocalDate, Long> countPostsByDate(Instant from) {
        Map<LocalDate, Long> countByDate = new HashMap<>();
        List<Object[]> rows = from == null
                ? rawPostEventRepository.countPostsByDateAll()
                : rawPostEventRepository.countPostsByDateFrom(from);

        for (Object[] row : rows) {
            countByDate.put(toLocalDate(row[0]), ((Number) row[1]).longValue());
        }
        return countByDate;
    }

    private Map<LocalDate, Long> countMessagesByDate(Instant from) {
        Map<LocalDate, Long> countByDate = new HashMap<>();
        List<Object[]> rows = from == null
                ? rawMessageEventRepository.countMessagesByDateAll()
                : rawMessageEventRepository.countMessagesByDateFrom(from);

        for (Object[] row : rows) {
            countByDate.put(toLocalDate(row[0]), ((Number) row[1]).longValue());
        }
        return countByDate;
    }

    private List<LocalDate> buildDateRange(Instant from, java.util.Set<LocalDate> firstDates, java.util.Set<LocalDate> secondDates) {
        TreeSet<LocalDate> allDates = new TreeSet<>();
        allDates.addAll(firstDates);
        allDates.addAll(secondDates);

        if (from != null) {
            LocalDate start = from.atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate end = LocalDate.now(ZoneOffset.UTC);
            List<LocalDate> range = new ArrayList<>();
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                range.add(date);
            }
            return range;
        }

        if (allDates.isEmpty()) {
            return List.of();
        }

        LocalDate start = allDates.first();
        LocalDate end = allDates.last();
        List<LocalDate> range = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            range.add(date);
        }
        return range;
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private Instant resolveFrom(String timeRange) {
        String normalized = timeRange == null ? "alltime" : timeRange.trim().toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        return switch (normalized) {
            case "today" -> today.atStartOfDay().toInstant(ZoneOffset.UTC);
            case "last7days", "7d", "last_7_days" -> today.minusDays(6).atStartOfDay().toInstant(ZoneOffset.UTC);
            case "last30days", "30d", "last_30_days" -> today.minusDays(29).atStartOfDay().toInstant(ZoneOffset.UTC);
            case "all", "alltime", "all_time" -> null;
            default -> null;
        };
    }
}
