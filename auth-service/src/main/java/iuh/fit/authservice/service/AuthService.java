package iuh.fit.authservice.service;

import com.nimbusds.jose.JOSEException;
import iuh.fit.authservice.dto.request.*;
import iuh.fit.authservice.dto.response.*;
import iuh.fit.authservice.entity.LoginHistory;
import iuh.fit.authservice.entity.enums.*;
import iuh.fit.authservice.exception.AppException;
import iuh.fit.authservice.exception.ErrorCode;
import iuh.fit.authservice.repository.LoginHistoryRepository;
import iuh.fit.authservice.repository.httpclient.GoogleUserClient;
import iuh.fit.authservice.utils.ValidationUtils;
import org.springframework.transaction.annotation.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AuthService {

    LoginHistoryRepository loginHistoryRepository;
    GoogleUserClient googleUserClient;
    PasswordEncoder passwordEncoder;
    JwtService jwtService;
    SessionService sessionService;
    NotificationPublisher notificationPublisher;
    ValidationUtils validationUtils;
    RestTemplate restTemplate;
    UserServiceClient userServiceClient;
    UserSyncService userSyncService;

    @NonFinal
    @Value("${app.user.default-avatar}")
    String defaultAvatarUrl;

    @NonFinal
    @Value("${app.user.default-cover-photo}")
    String defaultCoverPhotoUrl;

    @NonFinal
    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    String CLIENT_ID;

    @NonFinal
    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    String CLIENT_SECRET;

    @NonFinal
    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    String REDIRECT_URI;

    private static final String GRANT_TYPE = "authorization_code";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";

    @Transactional
    public AuthenticationResponse localLogin(LocalLoginRequest request) {
        String identifier = request.getIdentifier();
        log.info("Local login attempt started for identifier: {}", identifier);


        boolean isEmail = identifier != null && identifier.contains("@");
        UserServiceClient.UserDto user;

        if (isEmail) {
            if (!validationUtils.isValidEmail(identifier)) {
                log.warn("Invalid email format: {}", identifier);
                throw new AppException(ErrorCode.INVALID_EMAIL_FORMAT);
            }
            user = userServiceClient.getUserByEmail(identifier);
        } else {
            if (!validationUtils.isValidPhone(identifier)) {
                log.warn("Invalid phone format: {}", identifier);
                throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);
            }
            user = userServiceClient.getUserByPhone(identifier);
        }

        String passwordHash = userServiceClient.getPasswordHash(user.getId());
        if (passwordHash == null) {
            log.warn("Password not set for userId: {}", user.getId());
            logLoginHistory(user, request.getIpAddress(), request.getDeviceInfo(),
                    LoginStatus.FAILED, LoginMethod.LOCAL, "Password not set");
            throw new AppException(ErrorCode.INCORRECT_PASSWORD);
        }

        validateUserStatus(user, request.getIpAddress(), request.getDeviceInfo());

        if (!passwordEncoder.matches(request.getPassword(), passwordHash)) {
            log.warn("Invalid password for userId: {}", user.getId());
            logLoginHistory(user, request.getIpAddress(), request.getDeviceInfo(),
                    LoginStatus.FAILED, LoginMethod.LOCAL, "Invalid password");
            throw new AppException(ErrorCode.INCORRECT_PASSWORD);
        }

        if (is2FAEnabled(user)) {
            if (request.getOtpCode() == null || request.getOtpCode().trim().isEmpty()) {
                log.info("2FA required for userId: {}", user.getId());
                sendTwoFactorOtp(user, request.getIpAddress(), request.getLocation());

                return AuthenticationResponse.builder()
                        .authenticated(false)
                        .requires2FA(true)
                        .requiresPhoneSetup(false)
                        .tempToken(jwtService.generateTempToken(user, "LOCAL"))
                        .build();
            }

            log.info("Validating 2FA OTP for userId: {}", user.getId());
            validateTwoFactorOtp(user, request.getOtpCode());
        }

        log.info("Local login successful for userId: {}", user.getId());
        return createAuthResponse(user, request, LoginMethod.LOCAL);
    }

    @Transactional
    public AuthenticationResponse googleAuth(GoogleAuthRequest request) {
        log.info("Google auth started - redirectUri: {}", request.getRedirectUri());

        String redirectUri = request.getRedirectUri() != null
                ? request.getRedirectUri()
                : REDIRECT_URI;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("code", request.getCode());
            params.add("client_id", CLIENT_ID);
            params.add("client_secret", CLIENT_SECRET);
            params.add("redirect_uri", redirectUri);
            params.add("grant_type", GRANT_TYPE);

            log.info("Google Auth Request - redirectUri: {}", redirectUri);

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(params, headers);

            ResponseEntity<GoogleTokenResponse> response = restTemplate.postForEntity(
                    GOOGLE_TOKEN_URL,
                    requestEntity,
                    GoogleTokenResponse.class
            );

            GoogleTokenResponse tokenResponse = response.getBody();

            if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
                log.error("Google token response is null or missing access token");
                throw new AppException(ErrorCode.GOOGLE_AUTH_FAILED);
            }

            log.info("Google token received successfully");

            var userInfo = googleUserClient.getUserInfo("json", tokenResponse.getAccessToken());

            log.info("Google User Info - googleId: {}, email: {}, name: {}",
                    userInfo.getGoogleId(), userInfo.getEmail(), userInfo.getName());

            if (userInfo.getEmail() == null || !validationUtils.isValidEmail(userInfo.getEmail())) {
                log.warn("Invalid email from Google");
                throw new AppException(ErrorCode.GOOGLE_AUTH_FAILED);
            }

            UserServiceClient.UserDto user = null;

            try {
                user = userServiceClient.getUserByGoogleId(userInfo.getGoogleId());
            } catch (AppException e) {
                user = null;
            }

            if (user == null) {
                log.info("User not found by googleId, trying email: {}", userInfo.getEmail());
                try {
                    user = userServiceClient.getUserByEmail(userInfo.getEmail());
                } catch (AppException e) {
                    user = null;
                }
            }

            if (user == null) {
                log.info("User not found, generating temp token for phone setup");
                String tempToken = jwtService.generateGoogleTempToken(userInfo);

                return AuthenticationResponse.builder()
                        .authenticated(false)
                        .requires2FA(false)
                        .requiresPhoneSetup(true)
                        .tempToken(tempToken)
                        .googleUserInfo(GoogleUserInfo.builder()
                                .googleId(userInfo.getGoogleId())
                                .email(userInfo.getEmail())
                                .name(userInfo.getName())
                                .picture(userInfo.getPicture())
                                .build())
                        .build();
            }

            validateUserStatus(user, request.getIpAddress(), request.getDeviceInfo());

            log.info("Creating auth response for Google login");
            return createAuthResponse(user, request, LoginMethod.GOOGLE);

        } catch (AppException e) {
            log.error("AppException in googleAuth: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in googleAuth", e);
            throw new AppException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }

    @Transactional
    public AuthenticationResponse completeGoogleRegistration(CompleteGoogleRegistrationRequest request) {
        log.info("Complete Google registration started");

        var googleInfo = jwtService.verifyGoogleTempToken(request.getTempToken());

        if (!validationUtils.isValidPhone(request.getPhone())) {
            throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);
        }

        if (userServiceClient.existsByPhone(request.getPhone())) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        if (userServiceClient.existsByEmail(googleInfo.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        if (userServiceClient.existsByGoogleId(googleInfo.getGoogleId())) {
            throw new AppException(ErrorCode.GOOGLE_ACCOUNT_ALREADY_LINKED);
        }


        UserServiceClient.CreateUserRequest createRequest = new UserServiceClient.CreateUserRequest(
                request.getPhone(),
                googleInfo.getEmail(),
                googleInfo.getGoogleId(),
                googleInfo.getName(),
                defaultAvatarUrl,
                defaultCoverPhotoUrl
        );

        UserServiceClient.UserDto user = userServiceClient.createUser(createRequest);
        log.info("Created new Google account: {}", user.getId());

        return createAuthResponse(user, request, LoginMethod.GOOGLE);
    }

    @Transactional
    public AuthenticationResponse verify2FAOtp(String tempToken, String otpCode,
                                               String deviceId, DeviceType deviceType,
                                               String ipAddress, String deviceInfo,
                                               boolean isBackupCode) {
        log.info("Verifying 2FA - isBackupCode: {}", isBackupCode);

        var userInfo = jwtService.verifyTempToken(tempToken);
        String userId = userInfo.get("userId");
        UserServiceClient.UserDto user = userServiceClient.getUserById(userId);

        if (isBackupCode) {

            boolean valid = userServiceClient.validateAndConsumeBackupCode(userId, otpCode);
            if (!valid) {
                log.warn("Invalid backup code for userId: {}", userId);
                throw new AppException(ErrorCode.INVALID_BACKUP_CODE);
            }
            log.info("Backup code validated for userId: {}", userId);
        } else {

            validateOtpViaNotificationService(null, user.getEmail(), otpCode, OtpType.TWO_FACTOR_AUTH);
        }

        LoginMethod loginMethod = LoginMethod.valueOf(
                userInfo.getOrDefault("loginMethod", "LOCAL")
        );

        String token = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken();

        userSyncService.ensureUserExists(user);
        userServiceClient.createSession(
                user.getId(), deviceId, null,
                ipAddress, deviceInfo,
                token, refreshToken,
                loginMethod.name(),
                deviceType != null ? deviceType.name() : "UNKNOWN"
        );
        logLoginHistory(user, ipAddress, deviceInfo, LoginStatus.SUCCESS, loginMethod, null);
        sendWelcomeEmailIfNeeded(user);
        userServiceClient.updateLastLogin(userId);
        notificationPublisher.publishUserLoginEvent(userId, loginMethod.name().toLowerCase());


        return AuthenticationResponse.builder()
                .token(token).refreshToken(refreshToken)
                .authenticated(true).requires2FA(false).requiresPhoneSetup(false)
                .build();
    }

    @Transactional
    public OtpResponse request2FAOtp(Request2FAOtpRequest request) {
        String identifier = request.getIdentifier();
        log.info("Request 2FA OTP for identifier: {}", identifier);

        boolean isEmail = identifier != null && identifier.contains("@");
        UserServiceClient.UserDto user;

        if (isEmail) {
            if (!validationUtils.isValidEmail(identifier)) {
                throw new AppException(ErrorCode.INVALID_EMAIL_FORMAT);
            }
            user = userServiceClient.getUserByEmail(identifier);
        } else {
            if (!validationUtils.isValidPhone(identifier)) {
                throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);
            }
            user = userServiceClient.getUserByPhone(identifier);
        }

        validateUserStatus(user, request.getIpAddress(), null);

        if (!is2FAEnabled(user)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        return sendTwoFactorOtp(user, request.getIpAddress(), request.getLocation());
    }

    @Transactional
    public void logout(LogoutRequest request) throws ParseException, JOSEException {
        log.info("Logout request received");

        try {
            var signToken = jwtService.verifyToken(request.getToken(), true);

            String jit = signToken.getJWTClaimsSet().getJWTID();
            Date expiryTime = signToken.getJWTClaimsSet().getExpirationTime();
            String userId = signToken.getJWTClaimsSet().getStringClaim("userId");

            jwtService.invalidateToken(
                    jit,
                    LocalDateTime.ofInstant(expiryTime.toInstant(), java.time.ZoneId.systemDefault()),
                    userId,
                    "ACCESS",
                    "User logout"
            );

            if (request.getDeviceId() != null) {
                sessionService.revokeSessionByDevice(userId, request.getDeviceId());
            }

            log.info("Logout successful for userId: {}", userId);
        } catch (AppException exception) {
            log.warn("Logout failed with AppException: {}", exception.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during logout", e);
        }
    }

    @Transactional
    public AuthenticationResponse refreshToken(RefreshRequest request) throws ParseException, JOSEException {
        log.info("Token refresh requested");

        try {
            var signJWT = jwtService.verifyToken(request.getToken(), true);

            var jit = signJWT.getJWTClaimsSet().getJWTID();
            var expiryTime = signJWT.getJWTClaimsSet().getExpirationTime();
            var userId = signJWT.getJWTClaimsSet().getStringClaim("userId");

            jwtService.invalidateToken(
                    jit,
                    LocalDateTime.ofInstant(expiryTime.toInstant(), java.time.ZoneId.systemDefault()),
                    null,
                    "REFRESH",
                    "Token refreshed"
            );

            UserServiceClient.UserDto user = userServiceClient.getUserById(userId);

            if (!Boolean.TRUE.equals(user.getIsActive()) || user.getDeletedAt() != null) {
                throw new AppException(ErrorCode.USER_NOT_ACTIVE);
            }

            if (Boolean.TRUE.equals(user.getIsBlocked())) {
                if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(LocalDateTime.now())) {
                    throw new AppException(ErrorCode.USER_BLOCKED);
                }
            }

            String token = jwtService.generateToken(user);
            String refreshToken = jwtService.generateRefreshToken();

            if (request.getDeviceId() != null) {
                sessionService.updateSessionTokens(request.getDeviceId(), userId, token, refreshToken);
            }

            log.info("Token refreshed successfully for userId: {}", userId);

            return AuthenticationResponse.builder()
                    .token(token)
                    .refreshToken(refreshToken)
                    .authenticated(true)
                    .requires2FA(false)
                    .requiresPhoneSetup(false)
                    .build();

        } catch (ParseException | JOSEException e) {
            log.warn("Token refresh failed");
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    @Transactional
    public OtpResponse requestEmailOtpLogin(RequestEmailLoginOtpRequest request) {
        log.info("Request email OTP login for email: {}", request.getEmail());

        if (!validationUtils.isValidEmail(request.getEmail())) {
            throw new AppException(ErrorCode.INVALID_EMAIL_FORMAT);
        }

        UserServiceClient.UserDto user = userServiceClient.getUserByEmail(request.getEmail());

        validateUserStatus(user, request.getIpAddress(), request.getDeviceInfo());

        notificationPublisher.sendOtpEmail(
                user.getEmail(),
                user.getFullName(),
                null,
                OtpType.LOGIN_OTP_EMAIL,
                request.getIpAddress(),
                request.getLocation()
        );

        return OtpResponse.builder()
                .email(validationUtils.maskEmail(user.getEmail()))
                .message("OTP has been sent to your email")
                .build();
    }

    @Transactional
    public AuthenticationResponse verifyEmailOtpLogin(VerifyEmailLoginOtpRequest request) {
        log.info("Verifying email OTP login for email: {}", request.getEmail());

        if (!validationUtils.isValidEmail(request.getEmail())) {
            throw new AppException(ErrorCode.INVALID_EMAIL_FORMAT);
        }

        validateOtpViaNotificationService(null, request.getEmail(), request.getOtpCode(), OtpType.LOGIN_OTP_EMAIL);

        UserServiceClient.UserDto user = userServiceClient.getUserByEmail(request.getEmail());

        log.info("Email OTP login successful for userId: {}", user.getId());
        return createAuthResponse(user, request, LoginMethod.OTP);
    }

    private boolean is2FAEnabled(UserServiceClient.UserDto user) {
        return Boolean.TRUE.equals(user.getTwoFactorEnabled());
    }

    private OtpResponse sendTwoFactorOtp(UserServiceClient.UserDto user, String ipAddress, String location) {
        log.info("Sending 2FA OTP to userId: {}", user.getId());

        notificationPublisher.sendOtpEmail(
                user.getEmail(),
                user.getFullName(),
                null,
                OtpType.TWO_FACTOR_AUTH,
                ipAddress,
                location
        );

        return OtpResponse.builder()
                .phone(user.getPhone())
                .email(validationUtils.maskEmail(user.getEmail()))
                .message("OTP has been sent to your email")
                .build();
    }

    private void sendWelcomeEmailIfNeeded(UserServiceClient.UserDto user) {
        if (Boolean.TRUE.equals(user.getIsFirstLogin()) && !Boolean.TRUE.equals(user.getWelcomeEmailSent())) {
            try {
                String email = user.getEmail();
                if (email == null) {
                    UserServiceClient.UserDto freshUser = userServiceClient.getUserById(user.getId());
                    email = freshUser.getEmail();
                }

                notificationPublisher.sendWelcomeEmailAsync(
                        user.getId(),
                        email,
                        user.getFullName(),
                        user.getPhone(),
                        true,
                        user.getGoogleId() != null
                );
                log.debug("Welcome email sent for first login, userId: {}", user.getId());
            } catch (Exception e) {
                log.error("Failed to send welcome email event for userId={}", user.getId(), e);
            }
        }
    }

    private void validateUserStatus(UserServiceClient.UserDto user, String ipAddress, String deviceInfo) {
        if (user.getDeletedAt() != null) {
            log.warn("Deleted account login attempt, userId: {}", user.getId());
            logLoginHistory(user, ipAddress, deviceInfo,
                    LoginStatus.FAILED, LoginMethod.LOCAL, "Account deleted");
            throw new AppException(ErrorCode.ACCOUNT_DELETED);
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            log.warn("Inactive account login attempt, userId: {}", user.getId());
            logLoginHistory(user, ipAddress, deviceInfo,
                    LoginStatus.FAILED, LoginMethod.LOCAL, "Account not active");
            throw new AppException(ErrorCode.USER_NOT_ACTIVE);
        }

        if (Boolean.TRUE.equals(user.getIsBlocked())) {
            if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(LocalDateTime.now())) {
                log.warn("Blocked account login attempt, userId: {}", user.getId());
                logLoginHistory(user, ipAddress, deviceInfo,
                        LoginStatus.FAILED, LoginMethod.LOCAL, "Account blocked");
                throw new AppException(ErrorCode.USER_BLOCKED);
            }
        }
    }

    private AuthenticationResponse createAuthResponse(UserServiceClient.UserDto user, Object request, LoginMethod loginMethod) {
        log.info("Creating full auth response for userId: {}", user.getId());

        String token = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken();

        String deviceId   = extractField(request, "getDeviceId");
        String deviceName = extractField(request, "getDeviceName");
        String ipAddress  = extractField(request, "getIpAddress");
        String deviceInfo = extractField(request, "getDeviceInfo");
        DeviceType deviceType = extractDeviceType(request);

        userSyncService.ensureUserExists(user);

        userServiceClient.createSession(
                user.getId(), deviceId, deviceName,
                ipAddress, deviceInfo,
                token, refreshToken,
                loginMethod.name(),
                deviceType.name()
        );

        logLoginHistory(user, ipAddress, deviceInfo, LoginStatus.SUCCESS, loginMethod, null);
        sendWelcomeEmailIfNeeded(user);
        userServiceClient.updateLastLogin(user.getId());
        notificationPublisher.publishUserLoginEvent(user.getId(), loginMethod.name().toLowerCase());

        return AuthenticationResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .authenticated(true)
                .requires2FA(false)
                .requiresPhoneSetup(false)
                .build();
    }

    private void logLoginHistory(UserServiceClient.UserDto user, String ipAddress, String userAgent,
                                 LoginStatus status, LoginMethod loginMethod, String additionalInfo) {
        try {
            LoginHistory loginHistory = LoginHistory.builder()
                    .userId(user.getId())
                    .ipAddress(ipAddress)
                    .deviceType(extractDeviceTypeFromUserAgent(userAgent))
                    .userAgent(userAgent)
                    .status(status)
                    .loginMethod(loginMethod)
                    .qrCodeId(loginMethod == LoginMethod.QR_CODE ? additionalInfo : null)
                    .failureReason(status == LoginStatus.FAILED ? additionalInfo : null)
                    .build();

            loginHistoryRepository.save(loginHistory);
            log.debug("Login history saved for userId: {} | Status: {}", user.getId(), status);
        } catch (Exception e) {
            log.error("Failed to save login history", e);
        }
    }

    private DeviceType extractDeviceTypeFromUserAgent(String deviceInfo) {
        if (deviceInfo == null) return DeviceType.UNKNOWN;

        String lower = deviceInfo.toLowerCase();
        if (lower.contains("mobile") || lower.contains("android") || lower.contains("iphone")) {
            return DeviceType.MOBILE;
        } else if (lower.contains("tablet") || lower.contains("ipad")) {
            return DeviceType.TABLET;
        } else if (lower.contains("smart-tv") || lower.contains("tv")) {
            return DeviceType.TV;
        } else {
            return DeviceType.DESKTOP;
        }
    }

    private DeviceType extractDeviceType(Object request) {
        try {
            Object deviceTypeObj = request.getClass().getMethod("getDeviceType").invoke(request);
            if (deviceTypeObj instanceof DeviceType) return (DeviceType) deviceTypeObj;
            if (deviceTypeObj instanceof String) return DeviceType.valueOf(((String) deviceTypeObj).toUpperCase());
            return DeviceType.UNKNOWN;
        } catch (Exception e) {
            return DeviceType.UNKNOWN;
        }
    }

    private String extractField(Object request, String methodName) {
        try {
            return (String) request.getClass().getMethod(methodName).invoke(request);
        } catch (Exception e) {
            return null;
        }
    }

    private void validateOtpViaNotificationService(String phone, String email, String otpCode, OtpType otpType) {
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("X-Internal-Key", notificationPublisher.getInternalApiKey());
            headers.set("Content-Type", "application/json");

            Map<String, Object> body = new HashMap<>();
            if (phone != null) body.put("phone", phone);
            if (email != null) body.put("email", email);
            body.put("code", otpCode);
            body.put("otpType", otpType.name());

            restTemplate.exchange(
                    notificationPublisher.getNotificationServiceUrl() + "/internal/notification/otp/validate",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Void.class
            );
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.warn("OTP validation failed - invalid OTP");
            throw new AppException(ErrorCode.OTP_INVALID);
        } catch (Exception e) {
            log.error("OTP validation failed: {}", e.getMessage());
            throw new AppException(ErrorCode.OTP_INVALID);
        }
    }

    private void validateTwoFactorOtp(UserServiceClient.UserDto user, String otpCode) {
        validateOtpViaNotificationService(null, user.getEmail(), otpCode, OtpType.TWO_FACTOR_AUTH);
    }
}