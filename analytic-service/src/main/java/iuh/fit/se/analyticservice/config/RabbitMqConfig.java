package iuh.fit.se.analyticservice.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String USER_LOGIN_QUEUE = "analytics.user.login.queue";
    public static final String USER_REGISTERED_QUEUE = "analytics.user.registered.queue";
    public static final String MESSAGE_SENT_QUEUE = "analytics.message.sent.queue";
    public static final String POST_CREATED_QUEUE = "analytics.post.created.queue";

    @Bean
    public Queue userLoginQueue() {
        return new Queue(USER_LOGIN_QUEUE, true);
    }

    @Bean
    public Queue userRegisteredQueue() {
        return new Queue(USER_REGISTERED_QUEUE, true);
    }

    @Bean
    public Queue messageSentQueue() {
        return new Queue(MESSAGE_SENT_QUEUE, true);
    }

    @Bean
    public Queue postCreatedQueue() {
        return new Queue(POST_CREATED_QUEUE, true);
    }
}
