package moderationservice.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String MODERATION_EVENTS_EXCHANGE = "moderation.events";
    public static final String MODERATION_DLX = "moderation.dlx";
    public static final String REVIEW_REQUESTS_QUEUE = "moderation.review.requests.queue";
    public static final String REVIEW_REQUESTS_DLQ = "moderation.review.requests.dlq";

    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("*");
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }

    @Bean
    public DirectExchange moderationEventsExchange(
            @Value("${moderation.rabbitmq.exchange:" + MODERATION_EVENTS_EXCHANGE + "}") String exchangeName) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public DirectExchange moderationDlx(
            @Value("${moderation.rabbitmq.dlx:" + MODERATION_DLX + "}") String exchangeName) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Queue reviewRequestsQueue(
            @Value("${moderation.rabbitmq.queue.review-requests:" + REVIEW_REQUESTS_QUEUE + "}") String queueName,
            @Value("${moderation.rabbitmq.dlx:" + MODERATION_DLX + "}") String dlxName,
            @Value("${moderation.rabbitmq.dlq.review-requests:" + REVIEW_REQUESTS_DLQ + "}") String dlqName) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", dlxName)
                .withArgument("x-dead-letter-routing-key", dlqName)
                .build();
    }

    @Bean
    public Queue reviewRequestsDlq(
            @Value("${moderation.rabbitmq.dlq.review-requests:" + REVIEW_REQUESTS_DLQ + "}") String queueName) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding reviewRequestsBinding(
            @Qualifier("reviewRequestsQueue") Queue reviewRequestsQueue,
            @Qualifier("moderationEventsExchange") DirectExchange moderationEventsExchange,
            @Value("${moderation.rabbitmq.routing-key.review-requests:" + REVIEW_REQUESTS_QUEUE + "}") String routingKey) {
        return BindingBuilder.bind(reviewRequestsQueue)
                .to(moderationEventsExchange)
                .with(routingKey);
    }

    @Bean
    public Binding reviewRequestsDlqBinding(
            @Qualifier("reviewRequestsDlq") Queue reviewRequestsDlq,
            @Qualifier("moderationDlx") DirectExchange moderationDlx,
            @Value("${moderation.rabbitmq.dlq.review-requests:" + REVIEW_REQUESTS_DLQ + "}") String routingKey) {
        return BindingBuilder.bind(reviewRequestsDlq)
                .to(moderationDlx)
                .with(routingKey);
    }
}
