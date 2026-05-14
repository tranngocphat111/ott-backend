package mediaservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import mediaservice.configs.MediaCompressionProperties;
import mediaservice.configs.MediaDeleteProperties;
import mediaservice.configs.MediaUploadProperties;
import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication
@EnableRabbit
@EnableJpaRepositories(basePackages = "mediaservice.repositories")
@EnableConfigurationProperties({
    MediaCompressionProperties.class,
    MediaDeleteProperties.class,
    MediaUploadProperties.class
})
//@EnableRedisRepositories(basePackages = "mediaservice.repositories")
public class MediaServiceSpringApplication {
    public static void main(String[] args) {
        SpringApplication.run(MediaServiceSpringApplication.class, args);
    }

    @PostConstruct
    public void init() {
        // Set default timezone to Vietnam (GMT+7)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

}
