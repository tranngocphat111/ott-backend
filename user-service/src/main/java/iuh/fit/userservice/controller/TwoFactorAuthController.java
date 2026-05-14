package iuh.fit.userservice.controller;

import iuh.fit.userservice.dto.request.*;
import iuh.fit.userservice.dto.response.*;
import iuh.fit.userservice.service.TwoFactorAuthService;
import iuh.fit.userservice.utils.ControllerUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
        OtpResponse response = twoFactorAuthService.request2FAEnable(userId,
                Request2FAEnableOtpRequest.builder().ipAddress(ipAddress).build());
        return ApiResponse.<OtpResponse>builder()
                .result(response).message("OTP has been sent to your email to enable 2FA").build();
    }

    @PostMapping("/enable")
    public ApiResponse<Enable2FAResponse> enable2FA(@Valid @RequestBody Enable2FARequest request) {
        Enable2FAResponse response = twoFactorAuthService.enable2FA(controllerUtils.getCurrentUserId(), request);
        return ApiResponse.<Enable2FAResponse>builder()
                .result(response).message("Two-factor authentication enabled successfully. Save your backup codes.").build();
    }

    @PostMapping("/disable/request")
    public ApiResponse<OtpResponse> request2FADisable(
            @Valid @RequestBody Request2FADisableOtpRequest request,
            HttpServletRequest httpRequest) {
        controllerUtils.enrichWithClientInfo(request, httpRequest);
        OtpResponse response = twoFactorAuthService.request2FADisable(controllerUtils.getCurrentUserId(), request);
        return ApiResponse.<OtpResponse>builder()
                .result(response).message("OTP has been sent to your email to disable 2FA").build();
    }

    @PostMapping("/disable")
    public ApiResponse<Void> disable2FA(@Valid @RequestBody Disable2FARequest request) {
        twoFactorAuthService.disable2FA(controllerUtils.getCurrentUserId(), request);
        return ApiResponse.<Void>builder().message("Two-factor authentication disabled successfully").build();
    }

    @GetMapping("/status")
    public ApiResponse<TwoFactorAuthStatus> get2FAStatus() {
        TwoFactorAuthStatus status = twoFactorAuthService.get2FAStatus(controllerUtils.getCurrentUserId());
        return ApiResponse.<TwoFactorAuthStatus>builder().result(status).build();
    }

    @GetMapping("/enabled")
    public ApiResponse<Boolean> is2FAEnabled() {
        boolean enabled = twoFactorAuthService.is2FAEnabled(controllerUtils.getCurrentUserId());
        return ApiResponse.<Boolean>builder().result(enabled).build();
    }
}