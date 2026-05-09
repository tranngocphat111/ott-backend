package iuh.fit.userservice.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    @Value("${rabbitmq.exchange.notification}")
    public String exchange;

    @Value("${rabbitmq.routing-key.welcome}")
    public String welcomeRoutingKey;

    @Value("${rabbitmq.routing-key.alert}")
    public String alertRoutingKey;

    @Value("${rabbitmq.exchange.user-events}")
    public String userEventsExchange;

    @Value("${rabbitmq.routing-key.user-created}")
    public String userCreatedRoutingKey;

    @Value("${rabbitmq.routing-key.user-updated:user.updated}")
    public String userUpdatedRoutingKey;

    @Value("${rabbitmq.routing-key.user-logout:user.logout}")
    public String userLogoutRoutingKey;

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    @Bean
    public org.springframework.amqp.core.TopicExchange userEventsExchange() {
        return new org.springframework.amqp.core.TopicExchange(userEventsExchange, true, false);
    }

    @Bean
    public org.springframework.amqp.core.Queue userUpdatedQueue() {
        return new org.springframework.amqp.core.Queue(userUpdatedQueue, true);
    }

    @Bean
    public org.springframework.amqp.core.Binding userUpdatedBinding(org.springframework.amqp.core.Queue userUpdatedQueue, org.springframework.amqp.core.TopicExchange userEventsExchange) {
        return org.springframework.amqp.core.BindingBuilder.bind(userUpdatedQueue).to(userEventsExchange).with(userUpdatedRoutingKey);
    }
}