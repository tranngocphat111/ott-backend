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
            return jwsObject.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    public String generateRefreshToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public SignedJWT verifyToken(String token, boolean isRefresh) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());
        SignedJWT signedJWT = SignedJWT.parse(token);

        Date expiryTime = (isRefresh)
                ? new Date(signedJWT.getJWTClaimsSet().getIssueTime().toInstant()
                .plus(REFRESH_EXPIRATION, ChronoUnit.SECONDS).toEpochMilli())
                : signedJWT.getJWTClaimsSet().getExpirationTime();

        var verified = signedJWT.verify(verifier);

        if (!(verified && expiryTime.after(new Date())))
            throw new AppException(ErrorCode.UNAUTHENTICATED);

        if (invalidatedTokenRepository.existsById(signedJWT.getJWTClaimsSet().getJWTID()))
            throw new AppException(ErrorCode.UNAUTHENTICATED);

        return signedJWT;
    }

    public IntrospectResponse introspect(IntrospectRequest request) throws JOSEException, ParseException {
        String token = request.getToken();
        boolean isValid = true;

        try {
            verifyToken(token, false);
        } catch (AppException e) {
            isValid = false;
        }

        return IntrospectResponse.builder()
                .valid(isValid)
                .build();
    }

    public void invalidateToken(String jwtId, LocalDateTime expiryTime, String userId, String tokenType, String reason) {
        InvalidatedToken invalidatedToken = InvalidatedToken.builder()
                .id(jwtId)
                .expiryTime(expiryTime)
                .tokenType(tokenType)
                .reason(reason)
                .build();

        invalidatedTokenRepository.save(invalidatedToken);
    }

    public long getExpiration() {
        return EXPIRATION;
    }

    public long getRefreshExpiration() {
        return REFRESH_EXPIRATION;
    }

    private String buildScope(AccountType accountType) {
        return switch (accountType) {
            case ADMIN -> "ADMIN";
            case OA -> "OA";
            case USER -> "USER";
        };
    }


    public String generateTempToken(UserServiceClient.UserDto user, String loginMethod) {
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
            return jwsObject.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    public String generateGoogleTempToken(GoogleUserInfo userInfo) {
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
            return jwsObject.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, String> verifyTempToken(String token) {
        try {
            JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());
            SignedJWT signedJWT = SignedJWT.parse(token);

            if (!signedJWT.verify(verifier)) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (!expiryTime.after(new Date())) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            Boolean isTemp = signedJWT.getJWTClaimsSet().getBooleanClaim("temp");
            if (!Boolean.TRUE.equals(isTemp)) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            Map<String, String> claims = new HashMap<>();
            claims.put("userId", signedJWT.getJWTClaimsSet().getStringClaim("userId"));
            claims.put("purpose", signedJWT.getJWTClaimsSet().getStringClaim("purpose"));
            claims.put("loginMethod", signedJWT.getJWTClaimsSet().getStringClaim("loginMethod"));

            return claims;

        } catch (Exception e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    public GoogleUserInfo verifyGoogleTempToken(String token) {
        try {
            JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());
            SignedJWT signedJWT = SignedJWT.parse(token);

            if (!signedJWT.verify(verifier)) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (!expiryTime.after(new Date())) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            String purpose = signedJWT.getJWTClaimsSet().getStringClaim("purpose");
            if (!"GOOGLE_PHONE_SETUP".equals(purpose)) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            return GoogleUserInfo.builder()
                    .googleId(signedJWT.getJWTClaimsSet().getStringClaim("googleId"))
                    .email(signedJWT.getJWTClaimsSet().getStringClaim("email"))
                    .name(signedJWT.getJWTClaimsSet().getStringClaim("name"))
                    .picture(signedJWT.getJWTClaimsSet().getStringClaim("picture"))
                    .build();

        } catch (Exception e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }
}