package iuh.fit.authservice.controller;

import iuh.fit.authservice.exception.AppException;
import iuh.fit.authservice.exception.ErrorCode;
import iuh.fit.authservice.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/sessions")
@RequiredArgsConstructor
@Slf4j
public class InternalSessionController {

    private final SessionService sessionService;

    @Value("${internal.api-key}")
    private String internalApiKey;

    @PostMapping("/revoke-all/{userId}")
    public Map<String, Object> revokeAllSessions(
            @PathVariable String userId,
            @RequestHeader("X-Internal-Key") String key,
            @RequestBody(required = false) Map<String, String> body) {

        validateInternalKey(key);

        String reason = body != null ? body.getOrDefault("reason", "Revoked by internal service") : "Revoked by internal service";
        int revoked = sessionService.revokeAllUserSessions(userId, reason);

        log.info("Internal revoke-all completed for userId: {} | Revoked: {}", userId, revoked);
        return Map.of("revoked", revoked);
    }

    private void validateInternalKey(String key) {
        if (!internalApiKey.equals(key)) {
            log.warn("Invalid internal API key attempt on internal session endpoint");
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }
}

