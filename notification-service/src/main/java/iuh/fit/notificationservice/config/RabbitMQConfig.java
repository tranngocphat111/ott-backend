package iuh.fit.notificationservice.config;

import org.springframework.amqp.core.*;
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
    private String exchange;

    @Value("${rabbitmq.queue.otp}")
    private String otpQueue;

    @Value("${rabbitmq.queue.welcome}")
    private String welcomeQueue;

    @Value("${rabbitmq.queue.alert}")
    private String alertQueue;

    @Value("${rabbitmq.queue.inapp}")
    private String inappQueue;

    @Value("${rabbitmq.routing-key.otp}")
    private String otpRoutingKey;

    @Value("${rabbitmq.routing-key.welcome}")
    private String welcomeRoutingKey;

    @Value("${rabbitmq.routing-key.alert}")
    private String alertRoutingKey;

    @Value("${rabbitmq.routing-key.inapp}")
    private String inappRoutingKey;

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue otpQueue() {
        // OTP queue có Dead Letter Exchange để xử lý message thất bại
        return QueueBuilder.durable(otpQueue)
                .withArgument("x-dead-letter-exchange", "notification.dlx")
                .build();
    }

    @Bean
    public Queue welcomeQueue() {
        return QueueBuilder.durable(welcomeQueue).build();
    }

    @Bean
    public Queue alertQueue() {
        return QueueBuilder.durable(alertQueue).build();
    }

    @Bean
    public Queue inappQueue() {
        return QueueBuilder.durable(inappQueue).build();
    }

    @Bean
    public Binding otpBinding() {
        return BindingBuilder.bind(otpQueue()).to(notificationExchange()).with(otpRoutingKey);
    }

    @Bean
    public Binding welcomeBinding() {
        return BindingBuilder.bind(welcomeQueue()).to(notificationExchange()).with(welcomeRoutingKey);
    }

    @Bean
    public Binding alertBinding() {
        return BindingBuilder.bind(alertQueue()).to(notificationExchange()).with(alertRoutingKey);
    }

    @Bean
    public Binding inappBinding() {
        return BindingBuilder.bind(inappQueue()).to(notificationExchange()).with(inappRoutingKey);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange("notification.dlx");
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable("notification.dead-letter.queue").build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(otpQueue);
    }

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
}