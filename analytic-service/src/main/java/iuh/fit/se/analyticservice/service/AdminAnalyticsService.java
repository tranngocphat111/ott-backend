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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import iuh.fit.se.analyticservice.client.UserServiceClient;
import iuh.fit.se.analyticservice.dto.ApiResponseDTO;
import iuh.fit.se.analyticservice.dto.DailyActivityResponse;
import iuh.fit.se.analyticservice.dto.DailyPostCountResponse;
import iuh.fit.se.analyticservice.dto.DailyUserTrendResponse;
import iuh.fit.se.analyticservice.dto.PaginatedRecentUsersResponse;
import iuh.fit.se.analyticservice.dto.LoginMethodCountResponse;
import iuh.fit.se.analyticservice.dto.MessageTypesResponse;
import iuh.fit.se.analyticservice.dto.OverviewResponse;
import iuh.fit.se.analyticservice.dto.RecentNewUserDTO;
import iuh.fit.se.analyticservice.dto.UserDetailDTO;
import iuh.fit.se.analyticservice.entity.RawUserEvent;
import iuh.fit.se.analyticservice.repository.RawLoginEventRepository;
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
    private final RawLoginEventRepository rawLoginEventRepository;
    private final RawMessageEventRepository rawMessageEventRepository;
    private final RawPostEventRepository rawPostEventRepository;
    private final UserServiceClient userServiceClient;

    @Value("${internal.api.key:}")
    private String internalApiKey;

    public OverviewResponse getOverview(String timeRange) {
        Instant from = resolveFrom(timeRange);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant dauFrom = today.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant mauFrom = today.minusDays(29).atStartOfDay().toInstant(ZoneOffset.UTC);
        // current totals
        long totalUsers = from == null ? rawUserEventRepository.count() : rawUserEventRepository.countByTimestampGreaterThanEqual(from);
        long totalLogins = from == null ? rawLoginEventRepository.count() : rawLoginEventRepository.countByTimestampGreaterThanEqual(from);
        long totalMessages = from == null ? rawMessageEventRepository.count() : rawMessageEventRepository.countByTimestampGreaterThanEqual(from);
        long totalPosts = from == null ? rawPostEventRepository.count() : rawPostEventRepository.countByTimestampGreaterThanEqual(from);
        long dau = rawLoginEventRepository.countDistinctUsersFrom(dauFrom);
        long mau = rawLoginEventRepository.countDistinctUsersFrom(mauFrom);

        // if timeRange is not bounded (allTime) we cannot compute previous period -> deltas = null
        if (from == null) {
            return new OverviewResponse(totalUsers, totalLogins, totalMessages, totalPosts, dau, mau);
        }

        // compute previous period start by counting the number of days in the current range
        LocalDate startDate = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = LocalDate.now(ZoneOffset.UTC);
        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        Instant prevFrom = from.minus(java.time.Duration.ofDays(days));

        long usersSincePrevFrom = rawUserEventRepository.countByTimestampGreaterThanEqual(prevFrom);
        long prevUsers = Math.max(0, usersSincePrevFrom - (rawUserEventRepository.countByTimestampGreaterThanEqual(from)));

        long loginsSincePrevFrom = rawLoginEventRepository.countByTimestampGreaterThanEqual(prevFrom);
        long prevLogins = Math.max(0, loginsSincePrevFrom - (rawLoginEventRepository.countByTimestampGreaterThanEqual(from)));

        long messagesSincePrevFrom = rawMessageEventRepository.countByTimestampGreaterThanEqual(prevFrom);
        long prevMessages = Math.max(0, messagesSincePrevFrom - (rawMessageEventRepository.countByTimestampGreaterThanEqual(from)));

        long postsSincePrevFrom = rawPostEventRepository.countByTimestampGreaterThanEqual(prevFrom);
        long prevPosts = Math.max(0, postsSincePrevFrom - (rawPostEventRepository.countByTimestampGreaterThanEqual(from)));

        Double userDelta = computeDelta(prevUsers, totalUsers);
        Double loginDelta = computeDelta(prevLogins, totalLogins);
        Double messageDelta = computeDelta(prevMessages, totalMessages);
        Double postDelta = computeDelta(prevPosts, totalPosts);

        return new OverviewResponse(
            totalUsers,
            totalLogins,
            totalMessages,
            totalPosts,
            dau,
            mau,
            userDelta,
            loginDelta,
            messageDelta,
            postDelta
        );
    }

    public PaginatedRecentUsersResponse getRecentUsers(String timeRange, String query, int page, int size) {
        Instant from = resolveFrom(timeRange);
        List<RawUserEvent> recentEvents = from == null
                ? rawUserEventRepository.findAllByOrderByTimestampDesc()
                : rawUserEventRepository.findAllByTimestampGreaterThanEqualOrderByTimestampDesc(from);
        List<RecentNewUserDTO> result = new ArrayList<>();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        for (RawUserEvent event : recentEvents) {
            UserDetailDTO user = fetchUserDetail(event.getUserId());
            RecentNewUserDTO dto = new RecentNewUserDTO(
                    event.getUserId(),
                    user != null ? user.getEmail() : null,
                    user != null ? user.getFullName() : null,
                    event.getTimestamp(),
                    hasProfileDetails(user)
            );

            if (matchesQuery(dto, normalizedQuery)) {
                result.add(dto);
            }
        }

        int safeSize = size <= 0 ? 10 : Math.min(size, 100);
        int safePage = Math.max(page, 0);
        int totalElements = result.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        long requestedOffset = (long) safePage * safeSize;
        int fromIndex = (int) Math.min(requestedOffset, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);

        return new PaginatedRecentUsersResponse(
                result.subList(fromIndex, toIndex),
                totalElements,
                safePage,
                safeSize,
                totalPages
        );
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

    public List<LoginMethodCountResponse> getLoginMethods(String timeRange) {
        Instant from = resolveFrom(timeRange);
        List<Object[]> rows = from == null
                ? rawLoginEventRepository.countByLoginMethod()
                : rawLoginEventRepository.countByLoginMethodFrom(from);

        List<LoginMethodCountResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            String method = row[0] != null ? row[0].toString().toLowerCase(Locale.ROOT) : "unknown";
            long count = ((Number) row[1]).longValue();
            result.add(new LoginMethodCountResponse(method, count));
        }

        return result;
    }

    public List<DailyUserTrendResponse> getUserDailyTrend(String timeRange) {
        Instant from = resolveFrom(timeRange);
        Map<LocalDate, Long> registrationsByDate = countRegistrationsByDate(from);
        Map<LocalDate, Long> loginsByDate = countLoginsByDate(from);

        List<DailyUserTrendResponse> result = new ArrayList<>();
        for (LocalDate date : buildDateRange(from, registrationsByDate.keySet(), loginsByDate.keySet())) {
            result.add(new DailyUserTrendResponse(
                    date,
                    registrationsByDate.getOrDefault(date, 0L),
                    loginsByDate.getOrDefault(date, 0L)
            ));
        }
        return result;
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

    private Map<LocalDate, Long> countRegistrationsByDate(Instant from) {
        Map<LocalDate, Long> countByDate = new HashMap<>();
        List<Object[]> rows = from == null
                ? rawUserEventRepository.countRegistrationsByDateAll()
                : rawUserEventRepository.countRegistrationsByDateFrom(from);

        for (Object[] row : rows) {
            countByDate.put(toLocalDate(row[0]), ((Number) row[1]).longValue());
        }
        return countByDate;
    }

    private Map<LocalDate, Long> countLoginsByDate(Instant from) {
        Map<LocalDate, Long> countByDate = new HashMap<>();
        List<Object[]> rows = from == null
                ? rawLoginEventRepository.countLoginsByDateAll()
                : rawLoginEventRepository.countLoginsByDateFrom(from);

        for (Object[] row : rows) {
            countByDate.put(toLocalDate(row[0]), ((Number) row[1]).longValue());
        }
        return countByDate;
    }

    private boolean matchesQuery(RecentNewUserDTO dto, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }

        return containsIgnoreCase(dto.getUserId(), query)
                || containsIgnoreCase(dto.getEmail(), query)
                || containsIgnoreCase(dto.getFullName(), query);
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private UserDetailDTO fetchUserDetail(String userId) {
        if (internalApiKey == null || internalApiKey.isBlank()) {
            log.warn("INTERNAL_API_KEY is not configured, returning analytics event without profile details");
            return null;
        }

        try {
            ApiResponseDTO<UserDetailDTO> response = userServiceClient.getUserById(userId, internalApiKey);
            return response != null ? response.getResult() : null;
        } catch (RuntimeException ex) {
            log.warn("user-service unavailable for userId={}, returning analytics event without profile details",
                    userId, ex);
            return null;
        }
    }

    private boolean hasProfileDetails(UserDetailDTO user) {
        if (user == null) {
            return false;
        }

        return !isBlank(user.getEmail()) || !isBlank(user.getFullName()) || !isBlank(user.getPhone());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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

    private Double computeDelta(long previous, long current) {
        if (previous == 0) {
            if (current == 0) return 0.0;
            return 100.0;
        }
        return ((double) (current - previous) / (double) previous) * 100.0;
    }

    private Instant resolveFrom(String timeRange) {
        String normalized = timeRange == null ? "alltime" : timeRange.trim().toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        return switch (normalized) {
            case "today" -> today.atStartOfDay().toInstant(ZoneOffset.UTC);
            case "last7days", "7d", "last_7_days" -> today.minusDays(6).atStartOfDay().toInstant(ZoneOffset.UTC);
            case "last30days", "30d", "last_30_days" -> today.minusDays(29).atStartOfDay().toInstant(ZoneOffset.UTC);
            case "all", "alltime", "all_time" -> null;
            default -> throw new IllegalArgumentException("Unsupported timeRange: " + timeRange);
        };
    }
}
