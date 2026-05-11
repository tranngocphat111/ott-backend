package mediaservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.dtos.events.UserUpdatedEvent;
import mediaservice.repositories.AccountRepository;
import mediaservice.repositories.UserAccountRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserUpdatedEventConsumer {

    private final UserAccountRepository userAccountRepository;

    @RabbitListener(queues = "${user.updated.queue}")
    @Transactional
    public void handleUserUpdated(UserUpdatedEvent event) {
        if (event == null || event.getUserId() == null || event.getUserId().isBlank()) {
            log.warn("[UserUpdated] Invalid event: {}", event);
            return;
        }

        userAccountRepository.findById(event.getUserId()).ifPresentOrElse(user -> {
            boolean updated = false;

            if (event.getFullName() != null && !event.getFullName().isBlank()) {
                user.setDisplayName(event.getFullName());
                updated = true;
            }
            if (event.getAvatarUrl() != null && !event.getAvatarUrl().isBlank()) {
                user.setAvatarUrl(event.getAvatarUrl());
                updated = true;
            }
            if (event.getCoverUrl() != null && !event.getCoverUrl().isBlank()) {
                user.setCoverUrl(event.getCoverUrl());
                updated = true;
            }
            if (event.getBio() != null) {
                user.setBio(event.getBio());
                updated = true;
            }
            if (event.getWork() != null) {
                user.setWork(event.getWork());
                updated = true;
            }
            if (event.getLocation() != null) {
                user.setLocation(event.getLocation());
                updated = true;
            }
            if (event.getRelationshipStatus() != null) {
                user.setRelationshipStatus(event.getRelationshipStatus());
                updated = true;
            }
            if (event.getEmail() != null && !event.getEmail().isBlank()) {
                user.setEmail(event.getEmail());
                updated = true;
            }

            if (updated) {
                userAccountRepository.save(user);
                log.info("[UserUpdated] Successfully updated user account for userId={}", event.getUserId());
            } else {
                log.debug("[UserUpdated] No fields to update for userId={}", event.getUserId());
            }

        }, () -> {
            log.warn("[UserUpdated] Account not found for userId={}. Cannot update.", event.getUserId());
        });
    }
}
