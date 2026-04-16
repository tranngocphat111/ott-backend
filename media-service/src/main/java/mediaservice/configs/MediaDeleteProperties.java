package mediaservice.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "media.delete")
public class MediaDeleteProperties {
    private String exchange;
    private String queue;
    private String routingKey;
}
