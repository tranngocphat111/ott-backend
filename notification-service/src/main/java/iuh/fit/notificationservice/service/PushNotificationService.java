package iuh.fit.notificationservice.service;

import iuh.fit.notificationservice.dto.event.InAppNotificationEvent;
import iuh.fit.notificationservice.dto.request.PushTokenRequest;
import iuh.fit.notificationservice.entity.InAppNotification;
import iuh.fit.notificationservice.entity.PushToken;
import iuh.fit.notificationservice.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {
    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private final PushTokenRepository pushTokenRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Transactional
    public void registerToken(PushTokenRequest request) {
        String token = normalize(request.getToken());
        if (!isExpoPushToken(token)) {
            log.warn("Ignored invalid Expo push token for userId={}", request.getUserId());
            return;
        }

        deactivateTokensForDevice(request.getUserId(), normalize(request.getDeviceId()), normalize(request.getPlatform()), token);

        PushToken pushToken = pushTokenRepository.findByExpoPushToken(token)
                .orElseGet(PushToken::new);

        pushToken.setUserId(request.getUserId());
        pushToken.setExpoPushToken(token);
        pushToken.setPlatform(normalize(request.getPlatform()));
        pushToken.setDeviceId(normalize(request.getDeviceId()));
        pushToken.setActive(true);

        pushTokenRepository.save(pushToken);
        log.info(
                "Registered Expo push token for userId={}, platform={}, deviceId={}, tokenPrefix={}",
                request.getUserId(),
                normalize(request.getPlatform()),
                normalize(request.getDeviceId()),
                token.substring(0, Math.min(token.length(), 24))
        );
    }

    @Transactional
    public void unregisterToken(PushTokenRequest request) {
        String token = normalize(request.getToken());
        String deviceId = normalize(request.getDeviceId());
        String platform = normalize(request.getPlatform());
        boolean touched = false;

        if (token != null) {
            pushTokenRepository.findByExpoPushToken(token).ifPresent(pushToken -> {
                pushToken.setActive(false);
                pushTokenRepository.save(pushToken);
                log.info("Unregistered Expo push token for userId={}", pushToken.getUserId());
            });
            touched = true;
        }

        if (deviceId != null) {
            List<PushToken> sameUserDeviceTokens =
                    pushTokenRepository.findByUserIdAndDeviceIdAndActiveTrue(request.getUserId(), deviceId);
            sameUserDeviceTokens.forEach(pushToken -> {
                pushToken.setActive(false);
                pushTokenRepository.save(pushToken);
            });
            touched = touched || !sameUserDeviceTokens.isEmpty();
        }

        if (!touched) {
            log.warn("No Expo push token was unregistered for userId={}, deviceId={}, platform={}",
                    request.getUserId(), deviceId, platform);
        }
    }

    private void deactivateTokensForDevice(String userId, String deviceId, String platform, String exceptToken) {
        if (deviceId != null) {
            List<PushToken> sameUserDeviceTokens =
                    pushTokenRepository.findByUserIdAndDeviceIdAndActiveTrue(userId, deviceId);
            sameUserDeviceTokens.forEach(pushToken -> {
                if (exceptToken.equals(pushToken.getExpoPushToken())) return;
                pushToken.setActive(false);
                pushTokenRepository.save(pushToken);
            });
        }

        if (deviceId != null && platform != null) {
            List<PushToken> samePhysicalDeviceTokens =
                    pushTokenRepository.findByDeviceIdAndPlatformAndActiveTrue(deviceId, platform);
            samePhysicalDeviceTokens.forEach(pushToken -> {
                if (exceptToken.equals(pushToken.getExpoPushToken())) return;
                pushToken.setActive(false);
                pushTokenRepository.save(pushToken);
            });
        }
    }

    public void sendNotification(InAppNotification notification) {
        sendNotificationPayload(
                notification.getRecipientId(),
                String.valueOf(notification.getId()),
                notification.getType(),
                notification.getContent(),
                null,
                null,
                null,
                notification.getReferenceId(),
                notification.getSenderId(),
                false
        );
    }

    public void sendNotification(InAppNotificationEvent event) {
        sendNotificationPayload(
                event.getRecipientId(),
                null,
                event.getType(),
                event.getContent(),
                event.getTitle(),
                event.getBody(),
                event.getImageUrl(),
                event.getReferenceId(),
                event.getSenderId(),
                true
        );
    }

    private void sendNotificationPayload(
            String recipientId,
            String notificationId,
            String type,
            String content,
            String title,
            String body,
            String imageUrl,
            String referenceId,
            String senderId,
            boolean pushOnly
    ) {
        List<PushToken> tokens = pushTokenRepository.findByUserIdAndActiveTrue(recipientId);
        if (tokens.isEmpty()) {
            log.warn("No active Expo push tokens for userId={}, type={}", recipientId, type);
            return;
        }

        List<Map<String, Object>> messages = tokens.stream()
                .map(token -> buildExpoMessage(
                        token.getExpoPushToken(),
                        notificationId,
                        type,
                        content,
                        title,
                        body,
                        imageUrl,
                        referenceId,
                        senderId,
                        pushOnly
                ))
                .toList();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    EXPO_PUSH_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(messages, headers),
                    String.class
            );
            log.info(
                    "Sent Expo push notification type={} to userId={} tokens={} response={}",
                    type,
                    recipientId,
                    tokens.size(),
                    response.getBody()
            );
        } catch (Exception e) {
            log.warn("Failed to send Expo push notification type={} to userId={}: {}",
                    type, recipientId, e.getMessage());
        }
    }

    private Map<String, Object> buildExpoMessage(
            String token,
            String notificationId,
            String type,
            String content,
            String title,
            String body,
            String imageUrl,
            String referenceId,
            String senderId,
            boolean pushOnly
    ) {
        Map<String, Object> data = new HashMap<>();
        if (notificationId != null) {
            data.put("notificationId", notificationId);
        }
        data.put("type", type);
        data.put("referenceId", referenceId);
        data.put("senderId", senderId);
        data.put("pushOnly", pushOnly);
        if (imageUrl != null) {
            data.put("imageUrl", imageUrl);
        }

        Map<String, Object> message = new HashMap<>();
        message.put("to", token);
        message.put("title", normalize(title) != null ? normalize(title) : resolveTitle(type));
        message.put("body", normalize(body) != null ? normalize(body) : content);
        message.put("sound", "default");
        message.put("priority", "high");
        message.put("channelId", "riff-notifications");
        if (isHttpUrl(imageUrl)) {
            message.put("richContent", Map.of("image", imageUrl));
        }
        message.put("data", data);
        return message;
    }

    private String resolveTitle(String type) {
        String normalized = normalize(type);
        if (normalized == null) return "Riff";
        String lower = normalized.toLowerCase();
        if (lower.contains("friend") || lower.contains("relationship")) return "Lời mời kết bạn";
        if (lower.contains("call")) return "Cuộc gọi";
        if (lower.contains("group")) return "Nhóm";
        if (lower.contains("message")) return "Tin nhắn mới";
        return "Riff";
    }

    private boolean isExpoPushToken(String token) {
        return token != null
                && (token.startsWith("ExpoPushToken[") || token.startsWith("ExponentPushToken["));
    }

    private boolean isHttpUrl(String value) {
        String normalized = normalize(value);
        return normalized != null
                && (normalized.startsWith("http://") || normalized.startsWith("https://"));
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
