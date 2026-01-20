package iuh.fit.ottbackend.service;

import com.nimbusds.jose.JOSEException;
import iuh.fit.ottbackend.dto.request.*;
import iuh.fit.ottbackend.dto.response.*;
import iuh.fit.ottbackend.entity.*;
import iuh.fit.ottbackend.entity.enums.*;
import iuh.fit.ottbackend.exception.AppException;
import iuh.fit.ottbackend.exception.ErrorCode;
import iuh.fit.ottbackend.repository.LoginHistoryRepository;
import iuh.fit.ottbackend.repository.UserRepository;
import iuh.fit.ottbackend.repository.httpclient.GoogleUserClient;
import iuh.fit.ottbackend.utils.ValidationUtils;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.Date;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthService {

    UserRepository userRepository;
    LoginHistoryRepository loginHistoryRepository;
    GoogleUserClient googleUserClient;
    PasswordEncoder passwordEncoder;
    OtpService otpService;
    EmailService emailService;
    JwtService jwtService;
    SessionService sessionService;
    ValidationUtils validationUtils;
    RestTemplate restTemplate;

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
        if (!validationUtils.isValidPhone(request.getPhone())) {
            throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);
        }

        User user = userRepository.findByPhone(request.getPhone())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        if (user.getPasswordHash() == null) {
            logLoginHistory(user, request.getIpAddress(), request.getDeviceInfo(),
                    LoginStatus.FAILED, LoginMethod.LOCAL, "Password not set");
            throw new AppException(ErrorCode.PASSWORD_NOT_SET);
        }

        validateUserStatus(user, request.getIpAddress(), request.getDeviceInfo());

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            logLoginHistory(user, request.getIpAddress(), request.getDeviceInfo(),
                    LoginStatus.FAILED, LoginMethod.LOCAL, "Invalid password");
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (is2FAEnabled(user)) {
            if (request.getOtpCode() == null || request.getOtpCode().trim().isEmpty()) {
                sendTwoFactorOtp(user, request.getIpAddress(), request.getLocation());

                return AuthenticationResponse.builder()
                        .authenticated(false)
                        .requires2FA(true)
                        .requiresPhoneSetup(false)
                        .tempToken(jwtService.generateTempToken(user))
                        .build();
            }

            OtpCode otpCode = otpService.validateOtp(
                    request.getPhone(),
                    null,
                    request.getOtpCode(),
                    OtpType.TWO_FACTOR_AUTH
            );
            otpService.markOtpAsUsed(otpCode);
        }

        return createAuthResponse(user, request, LoginMethod.LOCAL);
    }

    @Transactional
    public AuthenticationResponse googleAuth(GoogleAuthRequest request) {
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

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(params, headers);

            ResponseEntity<GoogleTokenResponse> response = restTemplate.postForEntity(
                    GOOGLE_TOKEN_URL,
                    requestEntity,
                    GoogleTokenResponse.class
            );

            GoogleTokenResponse tokenResponse = response.getBody();

            if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
                throw new AppException(ErrorCode.GOOGLE_AUTH_FAILED);
            }

            var userInfo = googleUserClient.getUserInfo("json", tokenResponse.getAccessToken());

            if (userInfo.getEmail() == null || !validationUtils.isValidEmail(userInfo.getEmail())) {
                throw new AppException(ErrorCode.INVALID_GOOGLE_EMAIL);
            }


            User user = userRepository.findByGoogleId(userInfo.getGoogleId()).orElse(null);

            if (user == null) {
                user = userRepository.findByEmail(userInfo.getEmail()).orElse(null);
            }

            if (user == null) {
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

            if (user.getGoogleId() == null) {
                user.setGoogleId(userInfo.getGoogleId());

                if (user.getEmail() == null) {
                    user.setEmail(userInfo.getEmail());
                    user.setIsEmailVerified(true);
                    user.setEmailVerifiedAt(LocalDateTime.now());
                }

                if (user.getAvatarUrl() == null && userInfo.getPicture() != null) {
                    user.setAvatarUrl(userInfo.getPicture());
                }

                user = userRepository.save(user);
            }

            validateUserStatus(user, request.getIpAddress(), request.getDeviceInfo());

            if (is2FAEnabled(user)) {
                sendTwoFactorOtp(user, request.getIpAddress(), request.getLocation());

                return AuthenticationResponse.builder()
                        .authenticated(false)
                        .requires2FA(true)
                        .requiresPhoneSetup(false)
                        .tempToken(jwtService.generateTempToken(user))
                        .build();
            }

            return createAuthResponse(user, request, LoginMethod.GOOGLE);

        } catch (Exception e) {
            throw new AppException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }
    @Transactional
    public AuthenticationResponse completeGoogleRegistration(CompleteGoogleRegistrationRequest request) {
        var googleInfo = jwtService.verifyGoogleTempToken(request.getTempToken());

        if (!validationUtils.isValidPhone(request.getPhone())) {
            throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);
        }

        if (userRepository.existsByPhone(request.getPhone())) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        if (userRepository.existsByEmail(googleInfo.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .phone(request.getPhone())
                .email(googleInfo.getEmail())
                .googleId(googleInfo.getGoogleId())
                .fullName(googleInfo.getName())
                .avatarUrl(googleInfo.getPicture())
                .passwordHash(null)
                .accountType(AccountType.USER)
                .isPhoneVerified(true)
                .phoneVerifiedAt(now)
                .isEmailVerified(true)
                .emailVerifiedAt(now)
                .isActive(true)
                .isBlocked(false)
                .isFirstLogin(true)
                .welcomeEmailSent(false)
                .build();

        user = userRepository.save(user);
        return createAuthResponse(user, request, LoginMethod.GOOGLE);
    }

    @Transactional
    public AuthenticationResponse verify2FAOtp(String tempToken, String otpCode,
                                               String deviceId, DeviceType deviceType,
                                               String ipAddress, String deviceInfo) {
        var userInfo = jwtService.verifyTempToken(tempToken);
        String userId = userInfo.get("userId");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        OtpCode otpCode1 = otpService.validateOtp(
                user.getPhone(),
                null,
                otpCode,
                OtpType.TWO_FACTOR_AUTH
        );
        otpService.markOtpAsUsed(otpCode1);

        LoginMethod loginMethod = LoginMethod.valueOf(
                userInfo.getOrDefault("loginMethod", "LOCAL")
        );

        String token = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken();

        sessionService.createUserSession(
                user, deviceId, deviceType, null,
                ipAddress, deviceInfo, token, refreshToken, loginMethod
        );

        logLoginHistory(user, ipAddress, deviceInfo, LoginStatus.SUCCESS, loginMethod, null);

        sendWelcomeEmailIfNeeded(user);

        user.setLastLoginAt(LocalDateTime.now());
        user.setIsFirstLogin(false);
        userRepository.save(user);

        return AuthenticationResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .authenticated(true)
                .requires2FA(false)
                .requiresPhoneSetup(false)
                .build();
    }

    @Transactional
    public OtpResponse request2FAOtp(Request2FAOtpRequest request) {
        if (!validationUtils.isValidPhone(request.getPhone())) {
            throw new AppException(ErrorCode.INVALID_PHONE_FORMAT);
        }

        User user = userRepository.findByPhone(request.getPhone())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        validateUserStatus(user, request.getIpAddress(), null);

        if (!is2FAEnabled(user)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        return sendTwoFactorOtp(user, request.getIpAddress(), request.getLocation());
    }

    @Transactional
    public void logout(LogoutRequest request) throws ParseException, JOSEException {
        try {
            var signToken = jwtService.verifyToken(request.getToken(), true);

            String jit = signToken.getJWTClaimsSet().getJWTID();
            Date expiryTime = signToken.getJWTClaimsSet().getExpirationTime();
            String userId = signToken.getJWTClaimsSet().getStringClaim("userId");

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

            jwtService.invalidateToken(
                    jit,
                    LocalDateTime.ofInstant(expiryTime.toInstant(), java.time.ZoneId.systemDefault()),
                    user,
                    "ACCESS",
                    "User logout"
            );

            if (request.getDeviceId() != null) {
                sessionService.revokeSession(userId, request.getDeviceId());
            }
        } catch (AppException exception) {

        }
    }

    @Transactional
    public AuthenticationResponse refreshToken(RefreshRequest request) throws ParseException, JOSEException {
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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        if (!user.getIsActive() || user.getDeletedAt() != null) {
            throw new AppException(ErrorCode.USER_NOT_ACTIVE);
        }

        if (user.getIsBlocked()) {
            if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(LocalDateTime.now())) {
                throw new AppException(ErrorCode.USER_BLOCKED);
            }
            user.setIsBlocked(false);
            user.setBlockedUntil(null);
            user.setBlockedReason(null);
            userRepository.save(user);
        }

        String token = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken();

        if (request.getDeviceId() != null) {
            sessionService.updateSessionTokens(request.getDeviceId(), user, token, refreshToken);
        }

        return AuthenticationResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .authenticated(true)
                .requires2FA(false)
                .requiresPhoneSetup(false)
                .build();
    }

    private boolean is2FAEnabled(User user) {
        return user.getTwoFactorAuth() != null &&
                user.getTwoFactorAuth().getIsEnabled();
    }

    private OtpResponse sendTwoFactorOtp(User user, String ipAddress, String location) {
        OtpCode otpCode = otpService.generateOtp(
                user.getPhone(),
                user.getEmail(),
                OtpType.TWO_FACTOR_AUTH,
                ipAddress
        );

        emailService.sendOtpEmail(
                user.getEmail(),
                user.getFullName(),
                otpCode.getCode(),
                OtpType.TWO_FACTOR_AUTH,
                ipAddress,
                location
        );

        return OtpResponse.builder()
                .phone(user.getPhone())
                .email(validationUtils.maskEmail(user.getEmail()))
                .expiresAt(otpCode.getExpiresAt())
                .message("OTP has been sent to your email")
                .build();
    }

    private void sendWelcomeEmailIfNeeded(User user) {
        if (user.getIsFirstLogin() && !user.getWelcomeEmailSent()) {
            try {
                emailService.sendWelcomeEmail(user);
                user.setWelcomeEmailSent(true);
            } catch (Exception e) {
            }
        }
    }

    private void validateUserStatus(User user, String ipAddress, String deviceInfo) {
        if (user.getDeletedAt() != null) {
            logLoginHistory(user, ipAddress, deviceInfo,
                    LoginStatus.FAILED, LoginMethod.LOCAL, "Account deleted");
            throw new AppException(ErrorCode.ACCOUNT_DELETED);
        }

        if (!user.getIsActive()) {
            logLoginHistory(user, ipAddress, deviceInfo,
                    LoginStatus.FAILED, LoginMethod.LOCAL, "Account not active");
            throw new AppException(ErrorCode.USER_NOT_ACTIVE);
        }

        if (user.getIsBlocked()) {
            if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(LocalDateTime.now())) {
                logLoginHistory(user, ipAddress, deviceInfo,
                        LoginStatus.FAILED, LoginMethod.LOCAL, "Account blocked");
                throw new AppException(ErrorCode.USER_BLOCKED);
            }
            user.setIsBlocked(false);
            user.setBlockedUntil(null);
            user.setBlockedReason(null);
            userRepository.save(user);
        }
    }

    private AuthenticationResponse createAuthResponse(User user, Object request, LoginMethod loginMethod) {
        String token = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken();

        String deviceId = extractField(request, "getDeviceId");
        DeviceType deviceType = extractDeviceType(request);
        String deviceName = extractField(request, "getDeviceName");
        String ipAddress = extractField(request, "getIpAddress");
        String deviceInfo = extractField(request, "getDeviceInfo");

        sessionService.createUserSession(
                user, deviceId, deviceType, deviceName,
                ipAddress, deviceInfo, token, refreshToken, loginMethod
        );

        logLoginHistory(user, ipAddress, deviceInfo, LoginStatus.SUCCESS, loginMethod, null);

        sendWelcomeEmailIfNeeded(user);

        user.setLastLoginAt(LocalDateTime.now());
        user.setIsFirstLogin(false);
        userRepository.save(user);

        return AuthenticationResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .authenticated(true)
                .requires2FA(false)
                .requiresPhoneSetup(false)
                .build();
    }

    private void logLoginHistory(User user, String ipAddress, String userAgent,
                                 LoginStatus status, LoginMethod loginMethod, String additionalInfo) {
        LoginHistory loginHistory = LoginHistory.builder()
                .user(user)
                .ipAddress(ipAddress)
                .deviceType(extractDeviceTypeFromUserAgent(userAgent))
                .userAgent(userAgent)
                .status(status)
                .loginMethod(loginMethod)
                .qrCodeId(loginMethod == LoginMethod.QR_CODE ? additionalInfo : null)
                .failureReason(status == LoginStatus.FAILED ? additionalInfo : null)
                .build();

        loginHistoryRepository.save(loginHistory);
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
            String deviceTypeStr = (String) request.getClass()
                    .getMethod("getDeviceType").invoke(request);
            return DeviceType.valueOf(deviceTypeStr.toUpperCase());
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
}