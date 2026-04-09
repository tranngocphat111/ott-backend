package iuh.fit.authservice.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import iuh.fit.authservice.dto.request.IntrospectRequest;
import iuh.fit.authservice.dto.response.GoogleUserInfo;
import iuh.fit.authservice.dto.response.IntrospectResponse;
import iuh.fit.authservice.entity.InvalidatedToken;
import iuh.fit.authservice.entity.enums.AccountType;
import iuh.fit.authservice.exception.AppException;
import iuh.fit.authservice.exception.ErrorCode;
import iuh.fit.authservice.repository.InvalidatedTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    @NonFinal
    @Value("${jwt.secret}")
    protected String SIGNER_KEY;

    @NonFinal
    @Value("${jwt.expiration}")
    protected long EXPIRATION;

    @NonFinal
    @Value("${jwt.refresh-expiration}")
    protected long REFRESH_EXPIRATION;

    @NonFinal
    @Value("${jwt.2fa-temp-expiration}")
    protected long TWO_FA_TEMP_EXPIRATION;

    private final InvalidatedTokenRepository invalidatedTokenRepository;

    public String generateToken(UserServiceClient.UserDto user) {
        log.info("Generating access token for userId: {}", user.getId());

        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);
        AccountType accountType = AccountType.valueOf(user.getAccountType());
        String scope = buildScope(accountType);

        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(user.getPhone())
                .issuer("ottbackend.com")
                .issueTime(new Date())
                .expirationTime(new Date(
                        Instant.now().plus(EXPIRATION, ChronoUnit.SECONDS).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .claim("userId", user.getId())
                .claim("accountType", user.getAccountType())
                .claim("scope", scope)
                .claim("phone", user.getPhone())
                .claim("email", user.getEmail())
                .build();

        Payload payload = new Payload(jwtClaimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            String token = jwsObject.serialize();
            log.debug("Access token generated successfully for userId: {}", user.getId());
            return token;
        } catch (JOSEException e) {
            log.error("Failed to sign access token for userId: {}", user.getId(), e);
            throw new RuntimeException(e);
        }
    }

    public String generateRefreshToken() {
        log.debug("Generating refresh token");
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        log.debug("Refresh token generated successfully");
        return refreshToken;
    }

    public SignedJWT verifyToken(String token, boolean isRefresh) throws JOSEException, ParseException {
        log.debug("Verifying {} token", isRefresh ? "refresh" : "access");

        JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());
        SignedJWT signedJWT = SignedJWT.parse(token);

        Date expiryTime = (isRefresh)
                ? new Date(signedJWT.getJWTClaimsSet().getIssueTime().toInstant()
                .plus(REFRESH_EXPIRATION, ChronoUnit.SECONDS).toEpochMilli())
                : signedJWT.getJWTClaimsSet().getExpirationTime();

        var verified = signedJWT.verify(verifier);

        if (!(verified && expiryTime.after(new Date()))) {
            log.warn("Token verification failed: signature or expiration invalid");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (invalidatedTokenRepository.existsById(signedJWT.getJWTClaimsSet().getJWTID())) {
            log.warn("Token has been invalidated, jwtId: {}", signedJWT.getJWTClaimsSet().getJWTID());
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        log.debug("Token verified successfully, jwtId: {}", signedJWT.getJWTClaimsSet().getJWTID());
        return signedJWT;
    }

    public IntrospectResponse introspect(IntrospectRequest request) throws JOSEException, ParseException {
        log.info("Introspecting token");

        boolean isValid = true;
        try {
            verifyToken(request.getToken(), false);
            log.info("Token is valid");
        } catch (AppException e) {
            isValid = false;
            log.info("Token is invalid");
        }

        return IntrospectResponse.builder()
                .valid(isValid)
                .build();
    }

    public void invalidateToken(String jwtId, LocalDateTime expiryTime, String userId, String tokenType, String reason) {
        log.info("Invalidating token - jwtId: {}, type: {}, reason: {}", jwtId, tokenType, reason);

        InvalidatedToken invalidatedToken = InvalidatedToken.builder()
                .id(jwtId)
                .expiryTime(expiryTime)
                .tokenType(tokenType)
                .reason(reason)
                .build();

        invalidatedTokenRepository.save(invalidatedToken);
        log.debug("Token invalidated successfully");
    }

    public String generateTempToken(UserServiceClient.UserDto user, String loginMethod) {
        log.info("Generating 2FA temp token for userId: {}", user.getId());

        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(user.getPhone())
                .issuer("ottbackend.com")
                .issueTime(new Date())
                .expirationTime(new Date(
                        Instant.now().plus(5, ChronoUnit.MINUTES).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .claim("userId", user.getId())
                .claim("temp", true)
                .claim("purpose", "2FA_VERIFICATION")
                .claim("loginMethod", loginMethod != null ? loginMethod : "LOCAL")
                .build();

        Payload payload = new Payload(jwtClaimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            log.debug("Temp token generated for 2FA");
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Failed to generate temp token", e);
            throw new RuntimeException(e);
        }
    }

    public String generateGoogleTempToken(GoogleUserInfo userInfo) {
        log.info("Generating Google temp token for email: {}", userInfo.getEmail());

        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(userInfo.getEmail())
                .issuer("ottbackend.com")
                .issueTime(new Date())
                .expirationTime(new Date(
                        Instant.now().plus(10, ChronoUnit.MINUTES).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .claim("googleId", userInfo.getGoogleId())
                .claim("email", userInfo.getEmail())
                .claim("name", userInfo.getName())
                .claim("picture", userInfo.getPicture())
                .claim("temp", true)
                .claim("purpose", "GOOGLE_PHONE_SETUP")
                .build();

        Payload payload = new Payload(jwtClaimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            log.debug("Google temp token generated successfully");
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Failed to generate Google temp token", e);
            throw new RuntimeException(e);
        }
    }

    public Map<String, String> verifyTempToken(String token) {
        log.debug("Verifying temp token");

        try {
            JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());
            SignedJWT signedJWT = SignedJWT.parse(token);

            if (!signedJWT.verify(verifier)) {
                log.warn("Temp token signature verification failed");
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (!expiryTime.after(new Date())) {
                log.warn("Temp token has expired");
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            Boolean isTemp = signedJWT.getJWTClaimsSet().getBooleanClaim("temp");
            if (!Boolean.TRUE.equals(isTemp)) {
                log.warn("Token is not a temp token");
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            Map<String, String> claims = new HashMap<>();
            claims.put("userId", signedJWT.getJWTClaimsSet().getStringClaim("userId"));
            claims.put("purpose", signedJWT.getJWTClaimsSet().getStringClaim("purpose"));
            claims.put("loginMethod", signedJWT.getJWTClaimsSet().getStringClaim("loginMethod"));

            log.debug("Temp token verified successfully");
            return claims;

        } catch (Exception e) {
            log.warn("Temp token verification failed");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    public GoogleUserInfo verifyGoogleTempToken(String token) {
        log.debug("Verifying Google temp token");

        try {
            JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());
            SignedJWT signedJWT = SignedJWT.parse(token);

            if (!signedJWT.verify(verifier)) {
                log.warn("Google temp token signature verification failed");
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (!expiryTime.after(new Date())) {
                log.warn("Google temp token has expired");
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            String purpose = signedJWT.getJWTClaimsSet().getStringClaim("purpose");
            if (!"GOOGLE_PHONE_SETUP".equals(purpose)) {
                log.warn("Invalid purpose in Google temp token");
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            GoogleUserInfo userInfo = GoogleUserInfo.builder()
                    .googleId(signedJWT.getJWTClaimsSet().getStringClaim("googleId"))
                    .email(signedJWT.getJWTClaimsSet().getStringClaim("email"))
                    .name(signedJWT.getJWTClaimsSet().getStringClaim("name"))
                    .picture(signedJWT.getJWTClaimsSet().getStringClaim("picture"))
                    .build();

            log.debug("Google temp token verified successfully");
            return userInfo;

        } catch (Exception e) {
            log.warn("Google temp token verification failed");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    private String buildScope(AccountType accountType) {
        return switch (accountType) {
            case ADMIN -> "ADMIN";
            case OA -> "OA";
            case USER -> "USER";
        };
    }

    public long getExpiration() {
        return EXPIRATION;
    }

    public long getRefreshExpiration() {
        return REFRESH_EXPIRATION;
    }
}