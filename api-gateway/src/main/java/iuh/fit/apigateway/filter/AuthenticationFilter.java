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

@Component
@Slf4j
public class AuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final List<String> PUBLIC_ENDPOINTS = Arrays.asList(
            "/riff/api/auth/login/local",
            "/riff/api/auth/login/google",
            "/riff/api/auth/login/google/token",
            "/riff/api/auth/login/google/complete",
            "/riff/api/auth/login/email-otp/request",
            "/riff/api/auth/login/email-otp/verify",
            "/riff/api/auth/2fa/otp/request",
            "/riff/api/auth/2fa/verify",
            "/riff/api/auth/introspect",
            "/riff/api/auth/refresh",
            "/riff/api/auth/logout",
            "/riff/api/auth/qr/generate",
            "/riff/api/auth/qr/status/**",
            "/riff/api/users/register/otp",
            "/riff/api/users/register",

            "/riff/api/users/account/password/forgot/request",
            "/riff/api/users/account/password/forgot/verify",
            "/riff/api/users/account/password/forgot/otp/verify",


            "/actuator/**",
            "/riff/api/actuator/**",
            "/riff/api/ai/health",
            "/riff/api/chat/ai/health",
            "/socket.io/**",
            "/riff/api/chat/socket.io/**"   
    );

    private static final List<String> BLOCKED_EXTERNAL = Arrays.asList(
            "/riff/api/internal/**"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();

        log.info("Gateway request: {} {} | IP: {}", method, path, request.getRemoteAddress());

        // Block internal endpoints
        if (isBlocked(path)) {
            log.warn("Blocked external access to internal endpoint: {} {}", method, path);
            return writeErrorResponse(exchange, HttpStatus.FORBIDDEN, 1007, "Access denied");
        }

        if ("OPTIONS".equalsIgnoreCase(method)) {
            log.debug("CORS preflight request - bypassing authentication: {} {}", method, path);
            return chain.filter(exchange);
        }

        // Public endpoints - bypass auth
        if (isPublic(path)) {
            log.debug("Public endpoint - bypassing authentication: {} {}", method, path);
            return chain.filter(exchange);
        }

        log.debug("Protected endpoint requires authentication: {} {}", method, path);

        // Check Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header: {} {}", method, path);
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, 1006, "Unauthenticated");
        }

        String token = authHeader.substring(7);
        log.debug("Token received, length: {}", token.length());

        // JWT Verification
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(jwtSecret.getBytes());

            if (!signedJWT.verify(verifier)) {
                log.warn("JWT signature verification failed: {} {}", method, path);
                return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, 1006, "Token invalid");
            }

            Date expirationTime = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (expirationTime == null || !expirationTime.after(new Date())) {
                log.warn("Token has expired: {} | Expired at: {}", path, expirationTime);
                return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, 1006, "Token expired");
            }

            // Extract claims
            String userId = signedJWT.getJWTClaimsSet().getStringClaim("userId");
            String scope = signedJWT.getJWTClaimsSet().getStringClaim("scope");
            String phone = signedJWT.getJWTClaimsSet().getStringClaim("phone");
            String email = signedJWT.getJWTClaimsSet().getStringClaim("email");

            log.info("JWT verified successfully | UserId: {} | Path: {}", userId, path);

            // Inject headers for downstream services
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", userId != null ? userId : "")
                    .header("X-User-Scope", scope != null ? scope : "")
                    .header("X-User-Phone", phone != null ? phone : "")
                    .header("X-User-Email", email != null ? email : "")
                    .build();

            log.debug("Injected user context headers for downstream | UserId: {}", userId);

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (Exception e) {
            log.error("JWT processing failed | Path: {} | Error: {}", path, e.getMessage(), e);
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, 1006, "Token invalid");
        }
    }

    @Override
    public int getOrder() {
        return -1;
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
            log.debug("Error response written: {} - {}", status, message);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Failed to write error response body", e);
            return response.setComplete();
        }
    }
}
