package iuh.fit.notificationservice.controller;

import iuh.fit.notificationservice.dto.request.SendOtpEmailRequest;
import iuh.fit.notificationservice.dto.request.ValidateOtpRequest;
import iuh.fit.notificationservice.entity.enums.OtpType;
import iuh.fit.notificationservice.exception.AppException;
import iuh.fit.notificationservice.exception.ErrorCode;
import iuh.fit.notificationservice.service.EmailService;
import iuh.fit.notificationservice.service.OtpCacheService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/notification")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final EmailService emailService;
    private final OtpCacheService otpCacheService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @PostMapping("/otp/send")
    public ResponseEntity<Map<String, String>> sendOtpEmail(
            @RequestHeader("X-Internal-Key") String apiKey,
            @Valid @RequestBody SendOtpEmailRequest request) {

        validateKey(apiKey);

        if (otpCacheService.isRateLimited(request.getToEmail(), request.getOtpType())) {
            throw new AppException(ErrorCode.OTP_RATE_LIMIT_EXCEEDED);
        }

        String otp = (request.getOtpCode() != null && !request.getOtpCode().isBlank())
                ? request.getOtpCode()
                : String.format("%06d", new java.util.Random().nextInt(999999));

        otpCacheService.saveOtp(request.getToEmail(), request.getOtpType(), otp);

        emailService.sendOtpEmail(
                request.getToEmail(),
                request.getToName(),
                otp,  // ← dùng otp đã generate
                OtpType.valueOf(request.getOtpType()),
                request.getIpAddress(),
                request.getLocation(),
                request.getUserId()
        );

        log.info("OTP sent and cached for: {}", request.getToEmail());
        return ResponseEntity.ok(Map.of("message", "OTP sent successfully"));
    }

    @PostMapping("/otp/validate")
    public ResponseEntity<Map<String, Boolean>> validateOtp(
            @RequestHeader("X-Internal-Key") String apiKey,
            @Valid @RequestBody ValidateOtpRequest request) {

        validateKey(apiKey);

        boolean valid = otpCacheService.validateOtp(request.getEmail(), request.getOtpType(), request.getCode());

        if (valid) {
            otpCacheService.deleteOtp(request.getEmail(), request.getOtpType());
        }

        return ResponseEntity.ok(Map.of("valid", valid));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "notification-service"));
    }

    private void validateKey(String key) {
        if (!internalApiKey.equals(key)) {
            log.warn("Invalid internal API key attempt");
            throw new AppException(ErrorCode.UNAUTHORIZED, "Invalid internal API key");
        }
    }
}