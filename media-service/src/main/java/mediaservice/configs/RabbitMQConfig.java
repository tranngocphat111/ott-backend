package mediaservice.configs;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import mediaservice.configs.MediaDeleteProperties;
import mediaservice.configs.MediaUploadProperties;

@Configuration
public class RabbitMQConfig {

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
    public TopicExchange moderationEventsExchange(@Value("${moderation.rabbitmq.exchange}") String exchange) {
        return new TopicExchange(exchange, true, false);
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
        typeMapper.setTrustedPackages("*");
        typeMapper.setTypePrecedence(org.springframework.amqp.support.converter.Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
