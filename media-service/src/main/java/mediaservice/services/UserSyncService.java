package mediaservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.models.UserAccount;
import mediaservice.repositories.AccountRepository;
import mediaservice.repositories.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * Sync user data từ user-service vào media-service DB khi user chưa tồn tại.
 * Gọi HTTP trực tiếp (không qua RabbitMQ) đến internal endpoint của user-service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSyncService {

    private final RestTemplate restTemplate;
    private final UserAccountRepository userAccountRepository;
    private final AccountRepository accountRepository;
    private final org.springframework.cache.CacheManager cacheManager;

    @Value("${services.user.url}")
    private String userServiceUrl;

    @Value("${internal.api.key}")
    private String internalApiKey;

    /**
     * Đồng bộ dữ liệu user từ user-service.
     * Nếu userId chưa có hoặc đã có nhưng thiếu thông tin cơ bản (displayName/username), 
     * thực hiện kéo dữ liệu mới và lưu vào DB.
     *
     * @return UserAccount đã được sync
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UserAccount> syncUser(String userId) {
        if (userId == null || userId.isBlank()) return Optional.empty();

        Optional<UserAccount> existingOpt = userAccountRepository.findById(userId);
        
        // Nếu đã có và có đủ thông tin cơ bản thì không cần sync lại ngay
        if (existingOpt.isPresent()) {
            UserAccount existing = existingOpt.get();
            if (existing.getDisplayName() != null && existing.getUsername() != null) {
                return existingOpt;
            }
            log.info("[UserSync] User {} exists but lacks basic info (displayName/username). Refreshing...", userId);
        } else {
            log.info("[UserSync] User {} not found in media DB, fetching from user-service...", userId);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Key", internalApiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    userServiceUrl + "/internal/users/" + userId,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("[UserSync] user-service returned non-2xx for userId={}: {}", userId, response.getStatusCode());
                return Optional.empty();
            }

            // user-service wraps response in { "result": { ... } }
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.getBody().get("result");
            if (result == null) {
                log.warn("[UserSync] user-service returned empty result for userId={}", userId);
                return existingOpt;
            }

            UserAccount user = existingOpt.orElseGet(UserAccount::new);
            updateUserFields(user, userId, result);
            
            UserAccount saved = userAccountRepository.save(user);
            log.info("[UserSync] Successfully synced user {} from user-service", userId);
            
            // Xóa cache để lần gọi sau load lại từ DB với đầy đủ thông tin
            try {
                if (cacheManager.getCache("users") != null) {
                    cacheManager.getCache("users").evict(userId);
                    log.info("[UserSync] Evicted cache 'users' for userId: {}", userId);
                }
            } catch (Exception e) {
                log.warn("[UserSync] Failed to evict cache: {}", e.getMessage());
            }

            return Optional.of(saved);

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("[UserSync] user-service: user {} not found (404)", userId);
            return existingOpt;
        } catch (Exception e) {
            log.error("[UserSync] Failed to sync user {} from user-service: {}", userId, e.getMessage());
            return existingOpt;
        }
    }

    private void updateUserFields(UserAccount user, String userId, Map<String, Object> data) {
        log.info("[UserSync] Updating UserAccount for {} from data: {}", userId, data);
        user.setId(userId);

        // user-service dùng "fullName", media-service dùng "displayName"
        String displayName = getString(data, "fullName");
        if (displayName != null) user.setDisplayName(displayName);

        String email = getString(data, "email");
        if (email != null) user.setEmail(email);

        String avatarUrl = getString(data, "avatarUrl");
        if (avatarUrl != null) user.setAvatarUrl(avatarUrl);

        String coverUrl = getString(data, "coverUrl");
        if (coverUrl != null) user.setCoverUrl(coverUrl);

        // Tạo username từ email hoặc fullName nếu chưa có
        if (user.getUsername() == null) {
            user.setUsername(resolveUsername(email, displayName, userId));
        }

        log.info("[UserSync] Updated UserAccount: id={}, username={}, displayName={}", 
            user.getId(), user.getUsername(), user.getDisplayName());
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return (val instanceof String s && !s.isBlank()) ? s : null;
    }

    private String resolveUsername(String email, String displayName, String userId) {
        String base = null;
        if (email != null && email.contains("@")) {
            base = email.substring(0, email.indexOf('@'));
        } else if (displayName != null && !displayName.isBlank()) {
            base = displayName;
        }
        if (base == null || base.isBlank()) base = "user";

        String candidate = base.trim().replace(" ", "").toLowerCase();
        if (!accountRepository.existsByUsername(candidate)) return candidate;

        String suffix = userId.length() >= 6 ? userId.substring(0, 6) : userId;
        String withSuffix = candidate + "-" + suffix;
        if (!accountRepository.existsByUsername(withSuffix)) return withSuffix;

        int counter = 1;
        String fallback = withSuffix + "-" + counter;
        while (accountRepository.existsByUsername(fallback) && counter < 10) {
            fallback = withSuffix + "-" + (++counter);
        }
        return fallback;
    }
}
