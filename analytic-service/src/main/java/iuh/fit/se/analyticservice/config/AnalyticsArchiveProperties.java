package iuh.fit.se.analyticservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "analytics.archive")
public class AnalyticsArchiveProperties {

    private boolean enabled = false;

    private String cron = "0 30 2 * * *";

    private String zone = "UTC";

    private int retentionDays = 30;

    private int maxRowsPerFile = 50_000;

    private S3 s3 = new S3();

    @Data
    public static class S3 {
        private String bucketName = "";
        private String region = "ap-southeast-1";
        private String prefix = "analytics/raw-events";
        private boolean pathStyleAccessEnabled = false;
        private String endpoint = "";
    }
}
