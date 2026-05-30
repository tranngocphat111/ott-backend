package iuh.fit.se.analyticservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "analytics.daily-stats")
public class DailyStatsProperties {

    private boolean enabled = true;
    private String cron = "0 5 * * * *";
    private String zone = "UTC";
    private int lookbackDays = 2;
}
