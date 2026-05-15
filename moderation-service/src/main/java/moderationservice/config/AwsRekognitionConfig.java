package moderationservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;

@Configuration
public class AwsRekognitionConfig {

    @Bean
    public RekognitionClient rekognitionClient(@Value("${aws.region:ap-southeast-1}") String awsRegion) {
        return RekognitionClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }
}
