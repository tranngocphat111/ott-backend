package iuh.fit.se.analyticservice.config;

import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String USER_LOGIN_QUEUE = "analytics.user.login.queue";
    public static final String USER_REGISTERED_QUEUE = "analytics.user.registered.queue";
    public static final String USER_STATUS_CHANGED_QUEUE = "analytics.user.status.queue";
    public static final String MESSAGE_SENT_QUEUE = "analytics.message.sent.queue";
    public static final String POST_CREATED_QUEUE = "analytics.post.created.queue";
    public static final String USER_EVENTS_EXCHANGE = "user.events";
    public static final String USER_ROUTING_KEY_PATTERN = "user.#";
    public static final String USER_LOGIN_ROUTING_KEY = "user.login";
    public static final String USER_REGISTERED_ROUTING_KEY = "user.registered";
    public static final String USER_STATUS_CHANGED_ROUTING_KEY = "user.status.changed";
    public static final String CONTENT_VIOLATION_QUEUE = "analytics.content.violation.queue";
    public static final String ADMIN_AUDIT_QUEUE = "analytics.admin.audit.queue";
    public static final String ANALYTICS_DLX = "analytics.dlx";

    public static final String USER_LOGIN_DLQ = "analytics.user.login.dlq";
    public static final String USER_REGISTERED_DLQ = "analytics.user.registered.dlq";
    public static final String USER_STATUS_CHANGED_DLQ = "analytics.user.status.dlq";
    public static final String MESSAGE_SENT_DLQ = "analytics.message.sent.dlq";
    public static final String POST_CREATED_DLQ = "analytics.post.created.dlq";
    public static final String CONTENT_VIOLATION_DLQ = "analytics.content.violation.dlq";
    public static final String ADMIN_AUDIT_DLQ = "analytics.admin.audit.dlq";

    public static final String MODERATION_EVENTS_EXCHANGE = "moderation.events";
    public static final String MODERATION_VIOLATION_DETECTED_ROUTING_KEY = "moderation.violation.detected";
    public static final String MODERATION_ADMIN_AUDIT_ROUTING_KEY = "moderation.admin.audit";

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitAdmin.setAutoStartup(true);
        return rabbitAdmin;
    }

    @Bean
    public SmartLifecycle rabbitDeclarablesInitializer(RabbitAdmin rabbitAdmin) {
        return new SmartLifecycle() {
            private boolean running;

            @Override
            public void start() {
                rabbitAdmin.initialize();
                running = true;
            }

            @Override
            public void stop() {
                running = false;
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public boolean isAutoStartup() {
                return true;
            }

            @Override
            public int getPhase() {
                return Integer.MIN_VALUE;
            }
        };
    }

    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange(USER_EVENTS_EXCHANGE, true, false);
    }

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
    public Queue userStatusChangedQueue() {
        return QueueBuilder.durable(USER_STATUS_CHANGED_QUEUE)
                .withArguments(dlqArguments(USER_STATUS_CHANGED_DLQ))
                .build();
    }

    @Bean
    public Binding userLoginBinding(@Qualifier("userLoginQueue") Queue userLoginQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(userLoginQueue).to(userEventsExchange).with(USER_LOGIN_ROUTING_KEY);
    }

    @Bean
    public Binding userRegisteredBinding(@Qualifier("userRegisteredQueue") Queue userRegisteredQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(userRegisteredQueue).to(userEventsExchange).with(USER_REGISTERED_ROUTING_KEY);
    }

    @Bean
    public Binding userStatusChangedBinding(
            @Qualifier("userStatusChangedQueue") Queue userStatusChangedQueue,
            TopicExchange userEventsExchange) {
        return BindingBuilder.bind(userStatusChangedQueue).to(userEventsExchange).with(USER_STATUS_CHANGED_ROUTING_KEY);
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
    public Queue contentViolationQueue(
            @Value("${analytics.rabbitmq.queue.content-violation:" + CONTENT_VIOLATION_QUEUE + "}") String queueName,
            @Value("${analytics.rabbitmq.dlq.content-violation:" + CONTENT_VIOLATION_DLQ + "}") String dlqName) {
        return QueueBuilder.durable(queueName)
                .withArguments(dlqArguments(dlqName))
                .build();
    }

    @Bean
    public Queue adminAuditQueue(
            @Value("${analytics.rabbitmq.queue.admin-audit:" + ADMIN_AUDIT_QUEUE + "}") String queueName,
            @Value("${analytics.rabbitmq.dlq.admin-audit:" + ADMIN_AUDIT_DLQ + "}") String dlqName) {
        return QueueBuilder.durable(queueName)
                .withArguments(dlqArguments(dlqName))
                .build();
    }

    @Bean
    public DirectExchange moderationEventsExchange(
            @Value("${analytics.rabbitmq.exchange.moderation:" + MODERATION_EVENTS_EXCHANGE + "}") String exchangeName) {
        return new DirectExchange(exchangeName, true, false);
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
    public Queue userStatusChangedDlq() {
        return QueueBuilder.durable(USER_STATUS_CHANGED_DLQ).build();
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
    public Queue contentViolationDlq(
            @Value("${analytics.rabbitmq.dlq.content-violation:" + CONTENT_VIOLATION_DLQ + "}") String dlqName) {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Queue adminAuditDlq(
            @Value("${analytics.rabbitmq.dlq.admin-audit:" + ADMIN_AUDIT_DLQ + "}") String dlqName) {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding contentViolationBinding(
            @Qualifier("contentViolationQueue") Queue contentViolationQueue,
            DirectExchange moderationEventsExchange,
            @Value("${analytics.rabbitmq.routing-key.content-violation:" + MODERATION_VIOLATION_DETECTED_ROUTING_KEY + "}") String routingKey) {
        return BindingBuilder.bind(contentViolationQueue).to(moderationEventsExchange).with(routingKey);
    }

    @Bean
    public Binding adminAuditBinding(
            @Qualifier("adminAuditQueue") Queue adminAuditQueue,
            DirectExchange moderationEventsExchange,
            @Value("${analytics.rabbitmq.routing-key.admin-audit:" + MODERATION_ADMIN_AUDIT_ROUTING_KEY + "}") String routingKey) {
        return BindingBuilder.bind(adminAuditQueue).to(moderationEventsExchange).with(routingKey);
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
    public Binding userStatusChangedDlqBinding(
            @Qualifier("userStatusChangedDlq") Queue userStatusChangedDlq,
            DirectExchange analyticsDlx) {
        return BindingBuilder.bind(userStatusChangedDlq).to(analyticsDlx).with(USER_STATUS_CHANGED_DLQ);
    }

    @Bean
    public Binding messageSentDlqBinding(@Qualifier("messageSentDlq") Queue messageSentDlq, DirectExchange analyticsDlx) {
        return BindingBuilder.bind(messageSentDlq).to(analyticsDlx).with(MESSAGE_SENT_DLQ);
    }

    @Bean
    public Binding postCreatedDlqBinding(@Qualifier("postCreatedDlq") Queue postCreatedDlq, DirectExchange analyticsDlx) {
        return BindingBuilder.bind(postCreatedDlq).to(analyticsDlx).with(POST_CREATED_DLQ);
    }

    @Bean
    public Binding contentViolationDlqBinding(
            @Qualifier("contentViolationDlq") Queue contentViolationDlq,
            DirectExchange analyticsDlx,
            @Value("${analytics.rabbitmq.dlq.content-violation:" + CONTENT_VIOLATION_DLQ + "}") String dlqName) {
        return BindingBuilder.bind(contentViolationDlq).to(analyticsDlx).with(dlqName);
    }

    @Bean
    public Binding adminAuditDlqBinding(
            @Qualifier("adminAuditDlq") Queue adminAuditDlq,
            DirectExchange analyticsDlx,
            @Value("${analytics.rabbitmq.dlq.admin-audit:" + ADMIN_AUDIT_DLQ + "}") String dlqName) {
        return BindingBuilder.bind(adminAuditDlq).to(analyticsDlx).with(dlqName);
    }

    private Map<String, Object> dlqArguments(String deadLetterRoutingKey) {
        return Map.of(
                "x-dead-letter-exchange", ANALYTICS_DLX,
                "x-dead-letter-routing-key", deadLetterRoutingKey
        );
    }
}
