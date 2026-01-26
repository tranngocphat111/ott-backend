package iuh.fit.ottbackend.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private CustomJwtDecoder customJwtDecoder;

    private final String[] PUBLIC_ENDPOINTS = {

            "/auth/login/local",
            "/auth/login/google",
            "/auth/login/google/complete",
            "/auth/login/email-otp/request",
            "/auth/login/email-otp/verify",
            "/auth/2fa/otp/request",
            "/auth/2fa/verify",
            "/auth/refresh",
            "/auth/introspect",
            "/auth/logout",
            "/auth/qr/generate",
            "/auth/qr/status/**",
            "/auth/qr/cancel/**",
            "/users/register/otp",
            "/users/register",
            "/users/account/password/forgot/request",
            "/users/account/password/forgot/verify",
            "/otp/link/phone",
            "/otp/link/email"
    };

    private final String[] AUTHENTICATED_ENDPOINTS = {
            "/auth/qr/scan",
            "/auth/qr/confirm",
            "/users/profile/me",
            "/users/profile/me/**",
            "/users/account/password/set",
            "/users/account/password/change",
            "/users/account/email/change/request",
            "/users/account/email/change",
            "/users/account/phone/change/request",
            "/users/account/phone/change",
            "/users/account/delete/request",
            "/users/account",
            "/users/linking/phone",
            "/users/linking/email",
            "/users/2fa/enable/request",
            "/users/2fa/enable",
            "/users/2fa/disable/request",
            "/users/2fa/disable",
            "/users/2fa/status",
            "/users/2fa/enabled",
            "/users/sessions",
            "/users/sessions/**",
            "/users/**"
    };

    private final String[] OA_ENDPOINTS = {
            "/oa/**",
            "/content/manage/**",
            "/posts/**"
    };

    private final String[] ADMIN_ENDPOINTS = {
            "/admin/**",
            "/users/admin/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("GET", "/users/profile/{userId}").permitAll()
                        .requestMatchers(AUTHENTICATED_ENDPOINTS).authenticated()
                        .requestMatchers(OA_ENDPOINTS).hasAnyAuthority("ROLE_OA", "ROLE_ADMIN")
                        .requestMatchers(ADMIN_ENDPOINTS).hasAuthority("ROLE_ADMIN")
                        .anyRequest().authenticated()
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(customJwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                        .authenticationEntryPoint(new JwtAuthenticationEntryPoint())
                )

                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://192.168.*.*:*",
                "http://10.*.*.*:*",
                "http://172.16.*.*:*"
        ));

        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));

        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With"
        ));

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("scope");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        converter.setPrincipalClaimName("userId");

        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}