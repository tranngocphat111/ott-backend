package iuh.fit.apigateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import iuh.fit.apigateway.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Global filter thực hiện JWT authentication cho tất cả requests.
 *
 * Logic:
 * 1. Nếu request match PUBLIC_ENDPOINTS → bypass, forward thẳng
 * 2. Nếu không có Authorization header → 401
 * 3. Verify JWT locally bằng shared secret (HS512) → không gọi auth-service
 * 4. Nếu hợp lệ → forward + inject X-User-Id header để downstream services dùng
 * 5. Nếu không hợp lệ → 401
 */
@Component
@Slf4j
public class AuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Các endpoint không cần JWT.
     * Pattern dùng AntPathMatcher (/** = any path).
     */
    private static final List<String> PUBLIC_ENDPOINTS = Arrays.asList(
            // Auth endpoints
            "/riff/api/auth/login/local",
            "/riff/api/auth/login/google",
            "/riff/api/auth/login/google/complete",
            "/riff/api/auth/login/email-otp/request",
            "/riff/api/auth/login/email-otp/verify",
            "/riff/api/auth/2fa/otp/request",
            "/riff/api/auth/2fa/verify",
            "/riff/api/auth/introspect",
            "/riff/api/auth/refresh",
            "/riff/api/auth/logout",

            // QR public endpoints (generate + status polling không cần login)
            "/riff/api/auth/qr/generate",
            "/riff/api/auth/qr/status/**",

            // User registration
            "/riff/api/users/register/otp",
            "/riff/api/users/register",

            // Actuator (health check)
            "/actuator/**",
            "/riff/api/actuator/**"
    );

    /**
     * Internal endpoints — chỉ được gọi giữa các services, không từ bên ngoài.
     * Gateway sẽ BLOCK tất cả /internal/** từ client.
     */
    private static final List<String> BLOCKED_EXTERNAL = Arrays.asList(
            "/riff/api/internal/**"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        log.debug("Gateway received request: {} {}", request.getMethod(), path);

        // Block internal endpoints từ external clients
        if (isBlocked(path)) {
            log.warn("Blocked external access to internal endpoint: {}", path);
            return writeErrorResponse(exchange, HttpStatus.FORBIDDEN, 1007, "Access denied");
        }

        // Bypass JWT check cho public endpoints
        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        // Lấy Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, 1006, "Unauthenticated");
        }

        String token = authHeader.substring(7);

        // Verify JWT locally
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(jwtSecret.getBytes());

            if (!signedJWT.verify(verifier)) {
                return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, 1006, "Token invalid");
            }

            Date expirationTime = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (expirationTime == null || !expirationTime.after(new Date())) {
                return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, 1006, "Token expired");
            }

            // Inject user context headers cho downstream services
            String userId = signedJWT.getJWTClaimsSet().getStringClaim("userId");
            String scope = signedJWT.getJWTClaimsSet().getStringClaim("scope");
            String phone = signedJWT.getJWTClaimsSet().getStringClaim("phone");
            String email = signedJWT.getJWTClaimsSet().getStringClaim("email");

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId != null ? userId : "")
                    .header("X-User-Scope", scope != null ? scope : "")
                    .header("X-User-Phone", phone != null ? phone : "")
                    .header("X-User-Email", email != null ? email : "")
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (Exception e) {
            log.warn("JWT verification failed for path {}: {}", path, e.getMessage());
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, 1006, "Token invalid");
        }
    }

    @Override
    public int getOrder() {
        return -1; // Run before all other filters
    }

    private boolean isPublic(String path) {
        return PUBLIC_ENDPOINTS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean isBlocked(String path) {
        return BLOCKED_EXTERNAL.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange,
                                          HttpStatus status, int code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .code(code)
                .message(message)
                .build();

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }
}