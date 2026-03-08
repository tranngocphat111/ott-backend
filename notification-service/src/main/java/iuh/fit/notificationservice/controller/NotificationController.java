package iuh.fit.notificationservice.controller;

import iuh.fit.notificationservice.dto.request.SendOtpEmailRequest;
import iuh.fit.notificationservice.service.EmailService;
import iuh.fit.notificationservice.service.OtpCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    public ResponseEntity<Void> sendOtpEmail(
            @RequestHeader("X-Internal-Key") String apiKey,
            @RequestBody SendOtpEmailRequest request) {

        validateKey(apiKey);

        if (otpCacheService.isRateLimited(request.getToEmail(), request.getOtpType())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        otpCacheService.saveOtp(request.getToEmail(), request.getOtpType(), request.getOtpCode());

        emailService.sendOtpEmail(
                request.getToEmail(), request.getToName(),
                request.getOtpCode(), request.getOtpType(),
                request.getIpAddress(), request.getLocation(),
                request.getUserId()
        );

        return ResponseEntity.ok().build();
    }

    @PostMapping("/otp/validate")
    public ResponseEntity<Boolean> validateOtp(
            @RequestHeader("X-Internal-Key") String apiKey,
            @RequestParam String email,
            @RequestParam String otpType,
            @RequestParam String code) {

        validateKey(apiKey);
        boolean valid = otpCacheService.validateOtp(email, otpType, code);
        if (valid) otpCacheService.deleteOtp(email, otpType);
        return ResponseEntity.ok(valid);
    }

    private void validateKey(String key) {
        if (!internalApiKey.equals(key)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal API key");
        }
    }
}
