package mediaservice.configs;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
