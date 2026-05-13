package mediaservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.dtos.events.UserUpdatedEvent;
import mediaservice.repositories.AccountRepository;
import mediaservice.repositories.UserAccountRepository;
import org.springframework.cache.CacheManager;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserUpdatedEventConsumer {

    private final UserAccountRepository userAccountRepository;
    private final AccountRepository accountRepository;
    private final CacheManager cacheManager;

    @RabbitListener(queues = "${user.updated.queue}")
    @Transactional
    public void handleUserUpdated(UserUpdatedEvent event) {
        if (event == null || event.getUserId() == null || event.getUserId().isBlank()) {
            log.warn("[UserUpdated] Invalid event: {}", event);
            return;
        }

        // Evict cache BEFORE updating DB to ensure consistency
        evictCaches(event.getUserId());

        userAccountRepository.findById(event.getUserId()).ifPresentOrElse(user -> {
            boolean updated = false;

            // Name mapping
            if (event.getFullName() != null && !event.getFullName().isBlank()) {
                user.setDisplayName(event.getFullName());
                updated = true;
            } else if (event.getDisplayName() != null && !event.getDisplayName().isBlank()) {
                user.setDisplayName(event.getDisplayName());
                updated = true;
            }

            // Avatar mapping
            String avatar = event.getAvatarUrl() != null && !event.getAvatarUrl().isBlank() 
                ? event.getAvatarUrl() 
                : event.getAvatar();
            if (avatar != null && !avatar.isBlank()) {
                user.setAvatarUrl(avatar);
                updated = true;
            }

            // Cover mapping
            if (event.getCoverUrl() != null && !event.getCoverUrl().isBlank()) {
                user.setCoverUrl(event.getCoverUrl());
                updated = true;
            }

            // Other fields
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
            if (event.getPhone() != null && !event.getPhone().isBlank()) {
                user.setPhoneNumber(event.getPhone());
                updated = true;
            }

            if (updated) {
                userAccountRepository.save(user);
                log.info("[UserUpdated] Successfully updated user account for userId={}", event.getUserId());
            } else {
                log.debug("[UserUpdated] No fields to update for userId={}", event.getUserId());
            }

        }, () -> {
            log.warn("[UserUpdated] Account not found for userId={}. Creating new account (Upsert).", event.getUserId());
            mediaservice.models.UserAccount user = new mediaservice.models.UserAccount();
            user.setId(event.getUserId());
            
            String displayName = event.getFullName() != null ? event.getFullName() : event.getDisplayName();
            user.setDisplayName(displayName);
            user.setUsername(resolveUsername(event.getEmail(), displayName, event.getUserId()));
            
            user.setAvatarUrl(event.getAvatarUrl() != null ? event.getAvatarUrl() : event.getAvatar());
            user.setCoverUrl(event.getCoverUrl());
            user.setBio(event.getBio());
            user.setWork(event.getWork());
            user.setLocation(event.getLocation());
            user.setRelationshipStatus(event.getRelationshipStatus());
            user.setEmail(event.getEmail());
            user.setPhoneNumber(event.getPhone());
            
            userAccountRepository.save(user);
            log.info("[UserUpdated] Successfully created user account for userId={}", event.getUserId());
        });
    }

    private void evictCaches(String userId) {
        try {
            // Clear user profile caches
            Objects.requireNonNull(cacheManager.getCache("users")).evict(userId);
            Objects.requireNonNull(cacheManager.getCache("allUsers")).clear();
            
            // Clear post caches (author info is embedded in post responses)
            Objects.requireNonNull(cacheManager.getCache("userPosts")).evict(userId);
            Objects.requireNonNull(cacheManager.getCache("allPosts")).clear();
            
            log.info("[UserUpdated] Evicted all related caches for userId={}", userId);
        } catch (Exception e) {
            log.warn("[UserUpdated] Failed to evict caches for userId={}: {}", userId, e.getMessage());
        }
    }

    private String resolveUsername(String email, String displayName, String userId) {
        String base = null;
        if (email != null && email.contains("@")) {
            base = email.substring(0, email.indexOf('@'));
        } else if (displayName != null && !displayName.isBlank()) {
            base = displayName;
        }

        if (base == null || base.isBlank()) {
            base = "user";
        }

        String candidate = base.trim().replace(" ", "").toLowerCase();
        if (!accountRepository.existsByUsername(candidate)) {
            return candidate;
        }

        String suffix = userId.length() >= 6 ? userId.substring(0, 6) : userId;
        String withSuffix = candidate + "-" + suffix;
        if (!accountRepository.existsByUsername(withSuffix)) {
            return withSuffix;
        }

        int counter = 1;
        String fallback = withSuffix + "-" + counter;
        while (accountRepository.existsByUsername(fallback) && counter < 5) {
            counter++;
            fallback = withSuffix + "-" + counter;
        }
        return fallback;
    }
}
