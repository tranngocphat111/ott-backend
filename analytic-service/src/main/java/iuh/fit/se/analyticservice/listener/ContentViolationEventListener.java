package iuh.fit.se.analyticservice.listener;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import iuh.fit.se.analyticservice.dto.ContentViolationDetectedEvent;
import iuh.fit.se.analyticservice.entity.ContentViolationLog;
import iuh.fit.se.analyticservice.repository.ContentViolationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentViolationEventListener {

    private final ContentViolationLogRepository contentViolationLogRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${analytics.rabbitmq.queue.content-violation}")
    public void handleContentViolationEvent(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            ContentViolationDetectedEvent event = objectMapper.readValue(payload, ContentViolationDetectedEvent.class);
            validateEvent(event);

            String violationId = event.getViolationId().trim();
            if (contentViolationLogRepository.existsByViolationId(violationId)) {
                log.warn("Duplicate content violation ignored: violationId={}", violationId);
                return;
            }

            ContentViolationLog logEntry = ContentViolationLog.builder()
                    .violationId(violationId)
                    .sourceService(event.getSourceService())
                    .contentType(event.getContentType())
                    .contentRefId(event.getContentRefId())
                    .userId(event.getUserId())
                    .severity(event.getSeverity())
                    .violationType(event.getViolationType())
                    .matchedLabels(toCommaSeparatedLabels(event.getMatchedLabels()))
                    .detectedAt(toLocalDateTime(event.getDetectedAt()))
                    .build();

            contentViolationLogRepository.save(logEntry);
            log.info("Successfully recorded content violation {} from {}", violationId, event.getSourceService());
        } catch (DataIntegrityViolationException duplicate) {
            log.warn("Duplicate content violation ignored. payload={}", payload);
        } catch (AmqpRejectAndDontRequeueException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to parse/save content violation event. payload={}", payload, ex);
            throw new AmqpRejectAndDontRequeueException("Invalid content violation analytics event", ex);
        }
    }

    private void validateEvent(ContentViolationDetectedEvent event) {
        if (event == null || event.getViolationId() == null || event.getViolationId().isBlank()) {
            throw new AmqpRejectAndDontRequeueException("Missing required violationId in content violation event");
        }
    }

    private String toCommaSeparatedLabels(List<String> matchedLabels) {
        if (matchedLabels == null || matchedLabels.isEmpty()) {
            return "";
        }
        return String.join(",", matchedLabels);
    }

    private LocalDateTime toLocalDateTime(Instant detectedAt) {
        if (detectedAt == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(detectedAt, ZoneId.systemDefault());
    }
}
