package mediaservice.configs;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import mediaservice.configs.MediaDeleteProperties;
import mediaservice.configs.MediaUploadProperties;

@Configuration
public class RabbitMQConfig {

    private static final String ANALYTICS_DLX = "analytics.dlx";
    private static final String POST_CREATED_DLQ = "analytics.post.created.dlq";
    private static final String MEDIA_MODERATION_DLX = "media.moderation.dlx";

    @Bean
    public DirectExchange mediaCompressionExchange(MediaCompressionProperties properties) {
        return new DirectExchange(properties.getExchange());
    }

    @Bean
    public Queue mediaCompressionQueue(MediaCompressionProperties properties) {
        return new Queue(properties.getQueue());
    }

    @Bean
    public Binding mediaCompressionBinding(
            Queue mediaCompressionQueue,
            DirectExchange mediaCompressionExchange,
            MediaCompressionProperties properties) {
        return BindingBuilder.bind(mediaCompressionQueue)
                .to(mediaCompressionExchange)
                .with(properties.getRoutingKey());
    }

    @Bean
    public DirectExchange mediaUploadExchange(MediaUploadProperties properties) {
        return new DirectExchange(properties.getExchange());
    }

    @Bean
    public Queue mediaUploadQueue(MediaUploadProperties properties) {
        return new Queue(properties.getQueue());
    }

    @Bean
    public Binding mediaUploadBinding(
            Queue mediaUploadQueue,
            DirectExchange mediaUploadExchange,
            MediaUploadProperties properties) {
        return BindingBuilder.bind(mediaUploadQueue)
                .to(mediaUploadExchange)
                .with(properties.getRoutingKey());
    }

    @Bean
    public DirectExchange analyticsDlx() {
        return new DirectExchange(ANALYTICS_DLX, true, false);
    }

    @Bean
    public Queue analyticsPostCreatedQueue(
            @Value("${analytics.queue.post-created:analytics.post.created.queue}") String queueName) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", ANALYTICS_DLX)
                .withArgument("x-dead-letter-routing-key", POST_CREATED_DLQ)
                .build();
    }

    @Bean
    public Queue analyticsPostCreatedDlq() {
        return QueueBuilder.durable(POST_CREATED_DLQ).build();
    }

    @Bean
    public Binding analyticsPostCreatedDlqBinding(
            Queue analyticsPostCreatedDlq,
            DirectExchange analyticsDlx) {
        return BindingBuilder.bind(analyticsPostCreatedDlq)
                .to(analyticsDlx)
                .with(POST_CREATED_DLQ);
    }

    @Bean
    public DirectExchange mediaDeleteExchange(MediaDeleteProperties properties) {
        return new DirectExchange(properties.getExchange());
    }

    @Bean
    public Queue mediaDeleteQueue(MediaDeleteProperties properties) {
        return new Queue(properties.getQueue());
    }

    @Bean
    public Binding mediaDeleteBinding(
            Queue mediaDeleteQueue,
            DirectExchange mediaDeleteExchange,
            MediaDeleteProperties properties) {
        return BindingBuilder.bind(mediaDeleteQueue)
                .to(mediaDeleteExchange)
                .with(properties.getRoutingKey());
    }

    @Bean
    public DirectExchange moderationEventsExchange(@Value("${moderation.rabbitmq.exchange}") String exchange) {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    public DirectExchange mediaModerationDlx() {
        return new DirectExchange(MEDIA_MODERATION_DLX, true, false);
    }

    @Bean
    public Queue mediaModerationViolationQueue(
            @Value("${moderation.rabbitmq.queue.violation:media.moderation.violation.queue}") String queue,
            @Value("${moderation.rabbitmq.queue.violation-dlq:media.moderation.violation.dlq}") String dlq) {
        return QueueBuilder.durable(queue)
                .withArgument("x-dead-letter-exchange", MEDIA_MODERATION_DLX)
                .withArgument("x-dead-letter-routing-key", dlq)
                .build();
    }

    @Bean
    public Queue mediaModerationViolationDlq(
            @Value("${moderation.rabbitmq.queue.violation-dlq:media.moderation.violation.dlq}") String dlq) {
        return QueueBuilder.durable(dlq).build();
    }

    @Bean
    public Binding mediaModerationViolationBinding(
            Queue mediaModerationViolationQueue,
            DirectExchange moderationEventsExchange,
            @Value("${moderation.rabbitmq.routing-key.violation-detected:moderation.violation.detected}") String routingKey) {
        return BindingBuilder.bind(mediaModerationViolationQueue)
                .to(moderationEventsExchange)
                .with(routingKey);
    }

    @Bean
    public Binding mediaModerationViolationDlqBinding(
            Queue mediaModerationViolationDlq,
            DirectExchange mediaModerationDlx,
            @Value("${moderation.rabbitmq.queue.violation-dlq:media.moderation.violation.dlq}") String dlq) {
        return BindingBuilder.bind(mediaModerationViolationDlq)
                .to(mediaModerationDlx)
                .with(dlq);
    }

    @Bean
    public TopicExchange userEventsExchange(@Value("${user.events.exchange}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue userCreatedQueue(@Value("${user.created.queue}") String queue) {
        return new Queue(queue);
    }

    @Bean
    public Binding userCreatedBinding(
            Queue userCreatedQueue,
            TopicExchange userEventsExchange,
            @Value("${user.created.routing-key}") String routingKey) {
        return BindingBuilder.bind(userCreatedQueue)
                .to(userEventsExchange)
                .with(routingKey);
    }

    @Bean
    public Queue userUpdatedQueue(@Value("${user.updated.queue}") String queue) {
        return new Queue(queue);
    }

    @Bean
    public Binding userUpdatedBinding(
            Queue userUpdatedQueue,
            TopicExchange userEventsExchange,
            @Value("${user.updated.routing-key}") String routingKey) {
        return BindingBuilder.bind(userUpdatedQueue)
                .to(userEventsExchange)
                .with(routingKey);
    }

    @Bean
    public TopicExchange relationshipEventsExchange() {
        return new TopicExchange("relationship.events", true, false);
    }

    @Bean
    public TopicExchange postEventsExchange() {
        return new TopicExchange("post.events", true, false);
    }

    @Bean
    public Queue relationshipMediaQueue() {
        return new Queue("media_service_relationship_updates", true);
    }

    @Bean
    public Binding relationshipMediaBinding(Queue relationshipMediaQueue, TopicExchange relationshipEventsExchange) {
        return BindingBuilder.bind(relationshipMediaQueue)
                .to(relationshipEventsExchange)
                .with("relationship.#");
    }

    @Bean
    public Queue postMediaQueue() {
        return new Queue("media_service_post_updates", true);
    }

    @Bean
    public Binding postMediaBinding(Queue postMediaQueue, TopicExchange postEventsExchange) {
        return BindingBuilder.bind(postMediaQueue)
                .to(postEventsExchange)
                .with("post.#");
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper typeMapper = 
            new org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages(
            "mediaservice",
            "moderationservice",
            "moderationservice.contracts",
            "iuh.fit",
            "java.lang",
            "java.time",
            "java.util"
        );
        typeMapper.setTypePrecedence(org.springframework.amqp.support.converter.Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean(autowireCandidate = false)
    public MessageConverter rawRabbitMessageConverter() {
        return new MessageConverter() {
            @Override
            public Message toMessage(Object object, MessageProperties messageProperties) throws MessageConversionException {
                if (object instanceof Message message) {
                    return message;
                }
                throw new MessageConversionException("rawRabbitMessageConverter only supports raw AMQP messages");
            }

            @Override
            public Object fromMessage(Message message) throws MessageConversionException {
                return message;
            }
        };
    }
}
