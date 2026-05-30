package iuh.fit.se.analyticservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "analytics.dlq-monitor")
public class DlqMonitoringProperties {

    private boolean enabled = true;
    private String cron = "0 */5 * * * *";
    private long warnThreshold = 1;
}
