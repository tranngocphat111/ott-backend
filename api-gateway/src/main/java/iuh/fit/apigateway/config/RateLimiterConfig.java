package iuh.fit.apigateway.config;

import java.net.InetSocketAddress;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just(userId);
            }

            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            String fallbackKey = remoteAddress == null
                    ? "anonymous"
                    : remoteAddress.getAddress().getHostAddress();
            return Mono.just(fallbackKey);
        };
    }
}
