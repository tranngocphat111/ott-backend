package iuh.fit.notificationservice.service;

import iuh.fit.notificationservice.dto.event.InAppNotificationEvent;
import iuh.fit.notificationservice.entity.InAppNotification;
import iuh.fit.notificationservice.repository.InAppNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InAppNotificationService {
    private static final Logger log = LoggerFactory.getLogger(InAppNotificationService.class);

    private final InAppNotificationRepository repository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange.notification}")
    private String exchange;

    public void processNotificationEvent(InAppNotificationEvent event) {
        log.info("Processing in-app notification event for user: {}", event.getRecipientId());
        
        // Save to DB
        InAppNotification notification = InAppNotification.builder()
                .recipientId(event.getRecipientId())
                .senderId(event.getSenderId())
                .type(event.getType())
                .content(event.getContent())
                .referenceId(event.getReferenceId())
                .isRead(false)
                .build();
        
        InAppNotification savedNotification = repository.save(notification);

        // Publish to realtime queue for chat-service to emit via socket.io
        rabbitTemplate.convertAndSend(exchange, "notification.realtime", savedNotification);
    }

    public List<InAppNotification> getNotifications(String recipientId) {
        return repository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
    }
    
    public List<InAppNotification> getUnreadNotifications(String recipientId) {
        return repository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(recipientId);
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        repository.findById(notificationId).ifPresent(notification -> {
            notification.setRead(true);
            repository.save(notification);
        });
    }
    
    @Transactional
    public void markAllAsRead(String recipientId) {
        List<InAppNotification> unread = repository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(recipientId);
        unread.forEach(n -> n.setRead(true));
        repository.saveAll(unread);
    }
}
