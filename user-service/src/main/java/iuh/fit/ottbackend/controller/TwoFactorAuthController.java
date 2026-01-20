package iuh.fit.ottbackend.controller;

import iuh.fit.ottbackend.dto.request.*;
import iuh.fit.ottbackend.dto.response.*;
import iuh.fit.ottbackend.service.TwoFactorAuthService;
import iuh.fit.ottbackend.utils.ControllerUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/2fa")
@RequiredArgsConstructor
public class TwoFactorAuthController {

    private final TwoFactorAuthService twoFactorAuthService;
    private final ControllerUtils controllerUtils;

    @PostMapping("/enable/request")
    public ApiResponse<OtpResponse> request2FAEnable(HttpServletRequest httpRequest) {
        String userId = controllerUtils.getCurrentUserId();
        String ipAddress = controllerUtils.getClientIp(httpRequest);

        Request2FAEnableOtpRequest request = Request2FAEnableOtpRequest.builder()
                .ipAddress(ipAddress)
                .build();

        OtpResponse response = twoFactorAuthService.request2FAEnable(userId, request);

        return ApiResponse.<OtpResponse>builder()
                .result(response)
                .message("OTP has been sent to your email to enable 2FA")
                .build();
    }

    @PostMapping("/enable")
    public ApiResponse<Enable2FAResponse> enable2FA(
            @Valid @RequestBody Enable2FARequest request) {

        String userId = controllerUtils.getCurrentUserId();
        Enable2FAResponse response = twoFactorAuthService.enable2FA(userId, request);

        return ApiResponse.<Enable2FAResponse>builder()
                .result(response)
                .message("Two-factor authentication enabled successfully. Please save your backup codes.")
                .build();
    }

    @PostMapping("/disable/request")
    public ApiResponse<OtpResponse> request2FADisable(
            @Valid @RequestBody Request2FADisableOtpRequest request,
            HttpServletRequest httpRequest) {

        String userId = controllerUtils.getCurrentUserId();
        controllerUtils.enrichWithClientInfo(request, httpRequest);

        OtpResponse response = twoFactorAuthService.request2FADisable(userId, request);

        return ApiResponse.<OtpResponse>builder()
                .result(response)
                .message("OTP has been sent to your email to disable 2FA")
                .build();
    }

    @PostMapping("/disable")
    public ApiResponse<Void> disable2FA(@Valid @RequestBody Disable2FARequest request) {
        String userId = controllerUtils.getCurrentUserId();
        twoFactorAuthService.disable2FA(userId, request);

        return ApiResponse.<Void>builder()
                .message("Two-factor authentication disabled successfully")
                .build();
    }

    @GetMapping("/status")
    public ApiResponse<TwoFactorAuthStatus> get2FAStatus() {
        String userId = controllerUtils.getCurrentUserId();
        TwoFactorAuthStatus status = twoFactorAuthService.get2FAStatus(userId);

        return ApiResponse.<TwoFactorAuthStatus>builder()
                .result(status)
                .build();
    }

    @GetMapping("/enabled")
    public ApiResponse<Boolean> is2FAEnabled() {
        String userId = controllerUtils.getCurrentUserId();
        boolean enabled = twoFactorAuthService.is2FAEnabled(userId);

        return ApiResponse.<Boolean>builder()
                .result(enabled)
                .build();
    }
}