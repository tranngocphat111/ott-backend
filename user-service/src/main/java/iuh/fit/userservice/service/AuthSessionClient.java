package iuh.fit.userservice.service;

import com.nimbusds.jwt.SignedJWT;
import iuh.fit.userservice.exception.AppException;
import iuh.fit.userservice.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthSessionClient {

    private final RestTemplate restTemplate;

    @Value("${services.auth.url}")
    private String authServiceUrl;

    @Value("${internal.api.key}")
    private String internalApiKey;

    public int revokeAllSessions(String userId, String reason) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Key", internalApiKey);

            Map<String, String> body = Map.of("reason", reason != null ? reason : "Revoked by user-service");

            ResponseEntity<Map> response = restTemplate.exchange(
                    authServiceUrl + "/internal/sessions/revoke-all/" + userId,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            Object revoked = response.getBody() != null ? response.getBody().get("revoked") : null;
            if (revoked instanceof Number number) {
                return number.intValue();
            }
            return 0;
        } catch (Exception e) {
            log.error("Failed to revoke auth-service sessions for userId={}", userId, e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    public void revokeSession(String sessionToken, String userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Key", internalApiKey);

            String jwtId = extractJwtId(sessionToken);
            if (jwtId == null) {
                log.warn("Cannot extract jwtId from token, skipping auth-service revoke");
                return;
            }

            restTemplate.exchange(
                    authServiceUrl + "/internal/sessions/revoke/" + jwtId + "?userId=" + userId,
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("reason", "Revoked by user"), headers),
                    Map.class
            );
        } catch (Exception e) {
            // best-effort, không throw
            log.warn("Failed to blacklist token in auth-service: {}", e.getMessage());
        }
    }

    private String extractJwtId(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            return jwt.getJWTClaimsSet().getJWTID();
        } catch (Exception e) {
            log.warn("Failed to parse JWT token: {}", e.getMessage());
            return null;
        }
    }
}

