package iuh.fit.se.analyticservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "analytics.cache")
public class AnalyticsCacheProperties {

    private boolean enabled = true;
    private long dashboardTtlSeconds = 60;
}
