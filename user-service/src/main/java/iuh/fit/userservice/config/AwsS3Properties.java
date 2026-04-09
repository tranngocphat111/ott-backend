package iuh.fit.userservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aws.s3")
public class AwsS3Properties {
    private String accessKey;
    private String secretKey;
    private String region;
    private String bucket;
    private String defaultAvatar;
    private String defaultCoverPhoto;
}