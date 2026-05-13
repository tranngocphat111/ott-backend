package mediaservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.dtos.events.UserCreatedEvent;
import mediaservice.dtos.events.UserUpdatedEvent;
import mediaservice.models.UserAccount;
import mediaservice.repositories.AccountRepository;
import mediaservice.repositories.UserAccountRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserCreatedEventConsumer {

    private final UserAccountRepository userAccountRepository;
    private final AccountRepository accountRepository;

    @RabbitListener(queues = "${user.created.queue}")
    public void handleUserCreated(UserCreatedEvent event) {
        if (event == null || event.getUserId() == null || event.getUserId().isBlank()) {
            log.warn("[UserCreated] Invalid event: {}", event);
            return;
        }

        if (userAccountRepository.existsById(event.getUserId())) {
            log.debug("[UserCreated] Account already exists for userId={}", event.getUserId());
            return;
        }

        String username = resolveUsername(event);
        String displayName = event.getUsername() != null && !event.getUsername().isBlank()
                ? event.getUsername()
                : username;

        UserAccount user = new UserAccount();
        user.setId(event.getUserId());
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setEmail(event.getEmail());
        user.setPhoneNumber(event.getPhone());
        user.setAvatarUrl(event.getAvatar());
        if (event.getCoverUrl() != null && !event.getCoverUrl().isBlank()) {
            user.setCoverUrl(event.getCoverUrl());
        }
        if (event.getBio() != null) {
            user.setBio(event.getBio());
        }

        userAccountRepository.save(user);
        log.info("[UserCreated] Created user account for userId={}", event.getUserId());
    }



    private String resolveUsername(UserCreatedEvent event) {
        String base = null;
        if (event.getUsername() != null && !event.getUsername().isBlank()) {
            base = event.getUsername();
        } else if (event.getEmail() != null && event.getEmail().contains("@")) {
            base = event.getEmail().substring(0, event.getEmail().indexOf('@'));
        }

        if (base == null || base.isBlank()) {
            base = "user";
        }

        String candidate = base.trim().replace(" ", "").toLowerCase();
        if (!accountRepository.existsByUsername(candidate)) {
            return candidate;
        }

        String suffix = event.getUserId().length() >= 6 ? event.getUserId().substring(0, 6) : event.getUserId();
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
