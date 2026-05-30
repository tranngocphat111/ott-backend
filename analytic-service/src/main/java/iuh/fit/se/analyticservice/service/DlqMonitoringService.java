package iuh.fit.se.analyticservice.service;

import java.util.List;
import java.util.Properties;

import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import iuh.fit.se.analyticservice.config.DlqMonitoringProperties;
import iuh.fit.se.analyticservice.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DlqMonitoringService {

    private static final List<String> ANALYTICS_DLQ_NAMES = List.of(
            RabbitMqConfig.USER_LOGIN_DLQ,
            RabbitMqConfig.USER_REGISTERED_DLQ,
            RabbitMqConfig.USER_STATUS_CHANGED_DLQ,
            RabbitMqConfig.MESSAGE_SENT_DLQ,
            RabbitMqConfig.POST_CREATED_DLQ,
            RabbitMqConfig.CONTENT_VIOLATION_DLQ,
            RabbitMqConfig.ADMIN_AUDIT_DLQ
    );

    private final RabbitAdmin rabbitAdmin;
    private final DlqMonitoringProperties properties;

    @Scheduled(cron = "${analytics.dlq-monitor.cron}")
    public void inspectAnalyticsDlqQueues() {
        if (!properties.isEnabled()) {
            log.debug("Analytics DLQ monitoring is disabled");
            return;
        }

        for (String queueName : ANALYTICS_DLQ_NAMES) {
            inspectQueue(queueName);
        }
    }

    private void inspectQueue(String queueName) {
        Properties queueProperties = rabbitAdmin.getQueueProperties(queueName);
        if (queueProperties == null) {
            log.warn("Analytics DLQ does not exist: queue={}", queueName);
            return;
        }

        long messageCount = resolveMessageCount(queueProperties);
        if (messageCount >= properties.getWarnThreshold()) {
            log.warn("Analytics DLQ has pending messages: queue={}, messages={}", queueName, messageCount);
        } else {
            log.debug("Analytics DLQ healthy: queue={}, messages={}", queueName, messageCount);
        }
    }

    private long resolveMessageCount(Properties queueProperties) {
        Object value = queueProperties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
