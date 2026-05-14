package iuh.fit.se.analyticservice.config;

import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String USER_LOGIN_QUEUE = "analytics.user.login.queue";
    public static final String USER_REGISTERED_QUEUE = "analytics.user.registered.queue";
    public static final String MESSAGE_SENT_QUEUE = "analytics.message.sent.queue";
    public static final String POST_CREATED_QUEUE = "analytics.post.created.queue";
    public static final String ANALYTICS_DLX = "analytics.dlx";

    public static final String USER_LOGIN_DLQ = "analytics.user.login.dlq";
    public static final String USER_REGISTERED_DLQ = "analytics.user.registered.dlq";
    public static final String MESSAGE_SENT_DLQ = "analytics.message.sent.dlq";
    public static final String POST_CREATED_DLQ = "analytics.post.created.dlq";

    @Bean
    public Queue userLoginQueue() {
        return QueueBuilder.durable(USER_LOGIN_QUEUE)
                .withArguments(dlqArguments(USER_LOGIN_DLQ))
                .build();
    }

    @Bean
    public Queue userRegisteredQueue() {
        return QueueBuilder.durable(USER_REGISTERED_QUEUE)
                .withArguments(dlqArguments(USER_REGISTERED_DLQ))
                .build();
    }

    @Bean
    public Queue messageSentQueue() {
        return QueueBuilder.durable(MESSAGE_SENT_QUEUE)
                .withArguments(dlqArguments(MESSAGE_SENT_DLQ))
                .build();
    }

    @Bean
    public Queue postCreatedQueue() {
        return QueueBuilder.durable(POST_CREATED_QUEUE)
                .withArguments(dlqArguments(POST_CREATED_DLQ))
                .build();
    }

    @Bean
    public DirectExchange analyticsDlx() {
        return new DirectExchange(ANALYTICS_DLX, true, false);
    }

    @Bean
    public Queue userLoginDlq() {
        return QueueBuilder.durable(USER_LOGIN_DLQ).build();
    }

    @Bean
    public Queue userRegisteredDlq() {
        return QueueBuilder.durable(USER_REGISTERED_DLQ).build();
    }

    @Bean
    public Queue messageSentDlq() {
        return QueueBuilder.durable(MESSAGE_SENT_DLQ).build();
    }

    @Bean
    public Queue postCreatedDlq() {
        return QueueBuilder.durable(POST_CREATED_DLQ).build();
    }

    @Bean
    public Binding userLoginDlqBinding(@Qualifier("userLoginDlq") Queue userLoginDlq, DirectExchange analyticsDlx) {
        return BindingBuilder.bind(userLoginDlq).to(analyticsDlx).with(USER_LOGIN_DLQ);
    }

    @Bean
    public Binding userRegisteredDlqBinding(@Qualifier("userRegisteredDlq") Queue userRegisteredDlq, DirectExchange analyticsDlx) {
        return BindingBuilder.bind(userRegisteredDlq).to(analyticsDlx).with(USER_REGISTERED_DLQ);
    }

    @Bean
    public Binding messageSentDlqBinding(@Qualifier("messageSentDlq") Queue messageSentDlq, DirectExchange analyticsDlx) {
        return BindingBuilder.bind(messageSentDlq).to(analyticsDlx).with(MESSAGE_SENT_DLQ);
    }

    @Bean
    public Binding postCreatedDlqBinding(@Qualifier("postCreatedDlq") Queue postCreatedDlq, DirectExchange analyticsDlx) {
        return BindingBuilder.bind(postCreatedDlq).to(analyticsDlx).with(POST_CREATED_DLQ);
    }

    private Map<String, Object> dlqArguments(String deadLetterRoutingKey) {
        return Map.of(
                "x-dead-letter-exchange", ANALYTICS_DLX,
                "x-dead-letter-routing-key", deadLetterRoutingKey
        );
    }
}
