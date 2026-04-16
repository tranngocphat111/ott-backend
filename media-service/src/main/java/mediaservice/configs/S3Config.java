package mediaservice.configs;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    @Value("${aws.s3.transfer.multipart-threshold-mb:16}")
    private int multipartThresholdMb;

    @Value("${aws.s3.transfer.part-size-mb:8}")
    private int partSizeMb;

    @Value("${aws.s3.transfer.max-threads:16}")
    private int maxThreads;

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

    @Bean(destroyMethod = "shutdownNow")
    public TransferManager transferManager(AmazonS3 amazonS3) {
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(2, maxThreads));
        return TransferManagerBuilder.standard()
                .withS3Client(amazonS3)
            .withMultipartUploadThreshold((long) multipartThresholdMb * 1024 * 1024)
            .withMinimumUploadPartSize((long) partSizeMb * 1024 * 1024)
            .withExecutorFactory(() -> executor)
                .build();
    }
}

