package iuh.fit.se.analyticservice.service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import iuh.fit.se.analyticservice.config.AnalyticsArchiveProperties;
import iuh.fit.se.analyticservice.entity.AnalyticsArchiveRecord;
import iuh.fit.se.analyticservice.entity.RawLoginEvent;
import iuh.fit.se.analyticservice.entity.RawMessageEvent;
import iuh.fit.se.analyticservice.entity.RawPostEvent;
import iuh.fit.se.analyticservice.entity.RawRegistrationEvent;
import iuh.fit.se.analyticservice.entity.RawUserEvent;
import iuh.fit.se.analyticservice.enums.ArchiveStatus;
import iuh.fit.se.analyticservice.repository.AnalyticsArchiveRecordRepository;
import iuh.fit.se.analyticservice.repository.RawLoginEventRepository;
import iuh.fit.se.analyticservice.repository.RawMessageEventRepository;
import iuh.fit.se.analyticservice.repository.RawPostEventRepository;
import iuh.fit.se.analyticservice.repository.RawRegistrationEventRepository;
import iuh.fit.se.analyticservice.repository.RawUserEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsArchiveService {

    private final AnalyticsArchiveProperties properties;
    private final ObjectProvider<S3Client> s3ClientProvider;
    private final AnalyticsArchiveRecordRepository archiveRecordRepository;
    private final RawUserEventRepository rawUserEventRepository;
    private final RawRegistrationEventRepository rawRegistrationEventRepository;
    private final RawLoginEventRepository rawLoginEventRepository;
    private final RawMessageEventRepository rawMessageEventRepository;
    private final RawPostEventRepository rawPostEventRepository;

    @Scheduled(cron = "${analytics.archive.cron}", zone = "${analytics.archive.zone}")
    public void archiveExpiredRawEventsScheduled() {
        if (!properties.isEnabled()) {
            log.debug("Analytics archive job is disabled");
            return;
        }

        archiveExpiredRawEvents();
    }

    @Transactional
    public void archiveExpiredRawEvents() {
        String bucket = properties.getS3().getBucketName();
        if (bucket == null || bucket.isBlank()) {
            log.warn("Analytics archive is enabled but analytics.archive.s3.bucket-name is empty");
            return;
        }

        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null) {
            log.warn("Analytics archive is enabled but no S3 client is available");
            return;
        }

        ZoneId zone = ZoneId.of(properties.getZone());
        LocalDate archiveDate = LocalDate.now(zone).minusDays(Math.max(1, properties.getRetentionDays() + 1L));
        Instant start = archiveDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = archiveDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        int limit = Math.max(1, properties.getMaxRowsPerFile());

        archiveRawUserEvents(s3Client, bucket, archiveDate, start, end, limit);
        archiveRawRegistrationEvents(s3Client, bucket, archiveDate, start, end, limit);
        archiveRawLoginEvents(s3Client, bucket, archiveDate, start, end, limit);
        archiveRawMessageEvents(s3Client, bucket, archiveDate, start, end, limit);
        archiveRawPostEvents(s3Client, bucket, archiveDate, start, end, limit);
    }

    private void archiveRawUserEvents(S3Client s3Client, String bucket, LocalDate date, Instant start, Instant end, int limit) {
        archiveEventType(
                s3Client,
                bucket,
                "raw_user_events",
                date,
                start,
                end,
                limit,
                () -> rawUserEventRepository.countByTimestampGreaterThanEqualAndTimestampLessThan(start, end),
                () -> rawUserEventRepository.findByTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(start, end, PageRequest.of(0, limit)),
                () -> rawUserEventRepository.deleteByTimestampGreaterThanEqualAndTimestampLessThan(start, end),
                event -> List.of(event.getEventId(), event.getUserId(), event.getRegisterMethod(), String.valueOf(event.getTimestamp()))
        );
    }

    private void archiveRawRegistrationEvents(S3Client s3Client, String bucket, LocalDate date, Instant start, Instant end, int limit) {
        archiveEventType(
                s3Client,
                bucket,
                "raw_registration_events",
                date,
                start,
                end,
                limit,
                () -> rawRegistrationEventRepository.countByTimestampGreaterThanEqualAndTimestampLessThan(start, end),
                () -> rawRegistrationEventRepository.findByTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(start, end, PageRequest.of(0, limit)),
                () -> rawRegistrationEventRepository.deleteByTimestampGreaterThanEqualAndTimestampLessThan(start, end),
                event -> List.of(event.getEventId(), event.getUserId(), event.getRegisterMethod(), String.valueOf(event.getTimestamp()))
        );
    }

    private void archiveRawLoginEvents(S3Client s3Client, String bucket, LocalDate date, Instant start, Instant end, int limit) {
        archiveEventType(
                s3Client,
                bucket,
                "raw_login_events",
                date,
                start,
                end,
                limit,
                () -> rawLoginEventRepository.countByTimestampGreaterThanEqualAndTimestampLessThan(start, end),
                () -> rawLoginEventRepository.findByTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(start, end, PageRequest.of(0, limit)),
                () -> rawLoginEventRepository.deleteByTimestampGreaterThanEqualAndTimestampLessThan(start, end),
                event -> List.of(event.getEventId(), event.getUserId(), event.getLoginMethod(), String.valueOf(event.getTimestamp()))
        );
    }

    private void archiveRawMessageEvents(S3Client s3Client, String bucket, LocalDate date, Instant start, Instant end, int limit) {
        archiveEventType(
                s3Client,
                bucket,
                "raw_message_events",
                date,
                start,
                end,
                limit,
                () -> rawMessageEventRepository.countByTimestampGreaterThanEqualAndTimestampLessThan(start, end),
                () -> rawMessageEventRepository.findByTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(start, end, PageRequest.of(0, limit)),
                () -> rawMessageEventRepository.deleteByTimestampGreaterThanEqualAndTimestampLessThan(start, end),
                event -> List.of(event.getEventId(), nullToEmpty(event.getMessageId()), event.getUserId(), event.getMessageType(), String.valueOf(event.getTimestamp()))
        );
    }

    private void archiveRawPostEvents(S3Client s3Client, String bucket, LocalDate date, Instant start, Instant end, int limit) {
        archiveEventType(
                s3Client,
                bucket,
                "raw_post_events",
                date,
                start,
                end,
                limit,
                () -> rawPostEventRepository.countByTimestampGreaterThanEqualAndTimestampLessThan(start, end),
                () -> rawPostEventRepository.findByTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(start, end, PageRequest.of(0, limit)),
                () -> rawPostEventRepository.deleteByTimestampGreaterThanEqualAndTimestampLessThan(start, end),
                event -> List.of(event.getEventId(), event.getPostId(), event.getUserId(), String.valueOf(event.getTimestamp()))
        );
    }

    @Transactional
    private <T> void archiveEventType(
            S3Client s3Client,
            String bucket,
            String eventType,
            LocalDate archiveDate,
            Instant start,
            Instant end,
            int limit,
            LongSupplier countSupplier,
            Supplier<List<T>> rowsSupplier,
            LongSupplier deleteSupplier,
            Function<T, List<String>> rowMapper) {
        archiveRecordRepository.findByEventTypeAndArchiveDate(eventType, archiveDate)
                .filter(record -> record.getStatus() == ArchiveStatus.UPLOADED)
                .ifPresent(record -> log.debug("Archive already uploaded for eventType={}, date={}", eventType, archiveDate));

        boolean alreadyUploaded = archiveRecordRepository.findByEventTypeAndArchiveDate(eventType, archiveDate)
                .map(record -> record.getStatus() == ArchiveStatus.UPLOADED)
                .orElse(false);
        if (alreadyUploaded) {
            return;
        }

        long rowCount = countSupplier.getAsLong();
        if (rowCount == 0) {
            return;
        }
        if (rowCount > limit) {
            log.warn(
                    "Skipping archive for eventType={}, date={} because rowCount={} exceeds maxRowsPerFile={}",
                    eventType,
                    archiveDate,
                    rowCount,
                    limit
            );
            return;
        }

        String s3Key = buildS3Key(eventType, archiveDate);
        AnalyticsArchiveRecord record = archiveRecordRepository.findByEventTypeAndArchiveDate(eventType, archiveDate)
                .orElseGet(() -> AnalyticsArchiveRecord.builder()
                        .eventType(eventType)
                        .archiveDate(archiveDate)
                        .windowStart(start)
                        .windowEnd(end)
                        .s3Bucket(bucket)
                        .s3Key(s3Key)
                        .status(ArchiveStatus.IN_PROGRESS)
                        .build());

        try {
            List<T> rows = rowsSupplier.get();
            byte[] csvBytes = toCsv(rows, rowMapper);
            String sha256 = sha256(csvBytes);

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .contentType("text/csv")
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(csvBytes));

            long deletedRows = deleteSupplier.getAsLong();
            record.setRowCount(deletedRows);
            record.setContentSha256(sha256);
            record.setStatus(ArchiveStatus.UPLOADED);
            record.setArchivedAt(Instant.now());
            record.setErrorMessage(null);
            archiveRecordRepository.save(record);
            log.info("Archived {} rows for eventType={}, date={} to s3://{}/{}", deletedRows, eventType, archiveDate, bucket, s3Key);
        } catch (Exception ex) {
            record.setStatus(ArchiveStatus.FAILED);
            record.setErrorMessage(ex.getMessage());
            archiveRecordRepository.save(record);
            log.error("Failed to archive eventType={}, date={}", eventType, archiveDate, ex);
        }
    }

    private <T> byte[] toCsv(List<T> rows, Function<T, List<String>> rowMapper) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            for (T row : rows) {
                writer.println(toCsvLine(rowMapper.apply(row)));
            }
        }
        return output.toByteArray();
    }

    private String toCsvLine(List<String> values) {
        return values.stream()
                .map(this::escapeCsv)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private String escapeCsv(String value) {
        String safeValue = value == null ? "" : value;
        if (safeValue.contains(",") || safeValue.contains("\"") || safeValue.contains("\n") || safeValue.contains("\r")) {
            return "\"" + safeValue.replace("\"", "\"\"") + "\"";
        }
        return safeValue;
    }

    private String buildS3Key(String eventType, LocalDate archiveDate) {
        String prefix = properties.getS3().getPrefix();
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "analytics/raw-events"
                : prefix.replaceAll("/+$", "");
        return "%s/%s/%s.csv".formatted(normalizedPrefix, eventType, archiveDate);
    }

    private String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(bytes));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
