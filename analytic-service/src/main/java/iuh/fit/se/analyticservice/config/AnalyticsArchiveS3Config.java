package iuh.fit.se.analyticservice.config;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
public class AnalyticsArchiveS3Config {

    @Bean
    @ConditionalOnProperty(prefix = "analytics.archive", name = "enabled", havingValue = "true")
    public S3Client analyticsArchiveS3Client(AnalyticsArchiveProperties properties) {
        AnalyticsArchiveProperties.S3 s3 = properties.getS3();
        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(s3.isPathStyleAccessEnabled())
                .build();

        var builder = S3Client.builder()
                .region(Region.of(s3.getRegion()))
                .serviceConfiguration(serviceConfiguration);

        if (s3.getEndpoint() != null && !s3.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3.getEndpoint()));
        }

        return builder.build();
    }
}
