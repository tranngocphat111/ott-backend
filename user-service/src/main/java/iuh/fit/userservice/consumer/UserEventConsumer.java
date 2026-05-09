package iuh.fit.userservice.consumer;

import iuh.fit.userservice.dto.event.UserUpdatedEvent;
import iuh.fit.userservice.entity.User;
import iuh.fit.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEventConsumer {

    private final UserRepository userRepository;

    @RabbitListener(queues = "user.updated.queue")
    @Transactional
    public void handleUserUpdated(UserUpdatedEvent event) {
        log.info("Received user.updated event for userId: {}", event.getUserId());
        try {
            userRepository.findById(event.getUserId()).ifPresent(user -> {
                if (event.getAvatar() != null) user.setAvatarUrl(event.getAvatar());
                if (event.getCoverUrl() != null) user.setCoverUrl(event.getCoverUrl());
                if (event.getDisplayName() != null) user.setFullName(event.getDisplayName());
                if (event.getBio() != null) user.setBio(event.getBio());
                userRepository.save(user);
                log.info("Successfully updated user profile for userId: {}", event.getUserId());
            });
        } catch (Exception e) {
            log.error("Error processing user.updated event: {}", e.getMessage());
        }
    }
}
