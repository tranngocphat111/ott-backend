package mediaservice.configs;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class S3Config {

    @Value("${aws.social.s3.access-key}")
    private String accessKey;

    @Value("${aws.social.s3.secret-key}")
    private String secretKey;

    @Value("${aws.social.s3.region}")
    private String region;

    @Value("${aws.social.s3.use-local:false}")
    private boolean useLocal;

    @Value("${aws.social.s3.local-endpoint:http://localhost:4566}")
    private String localEndpoint;

    @Bean
    public AmazonS3 amazonS3() {
        AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);

        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials));

        // Use local endpoint for development (LocalStack, MinIO, etc.)
        if (useLocal) {
            builder.withEndpointConfiguration(
                    new AwsClientBuilder.EndpointConfiguration(localEndpoint, region)
            ).withPathStyleAccessEnabled(true);
        } else {
            builder.withRegion(region);
        }

        return builder.build();
    }
}

