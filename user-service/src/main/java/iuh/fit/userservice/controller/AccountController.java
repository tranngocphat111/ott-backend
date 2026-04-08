package iuh.fit.userservice.controller;

import iuh.fit.userservice.dto.request.*;
import iuh.fit.userservice.dto.response.*;
import iuh.fit.userservice.service.AccountService;
import iuh.fit.userservice.utils.ControllerUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final ControllerUtils controllerUtils;

    @PostMapping("/password/set")
    public ApiResponse<Void> setPassword(@Valid @RequestBody SetPasswordRequest request) {
        accountService.setPassword(controllerUtils.getCurrentUserId(), request);
        return ApiResponse.<Void>builder().message("Password set successfully").build();
    }

    @PostMapping("/password/change")
    public ApiResponse<PasswordChangeResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        controllerUtils.enrichWithClientInfo(request, httpRequest);
        PasswordChangeResponse response = accountService.changePassword(controllerUtils.getCurrentUserId(), request);
        return ApiResponse.<PasswordChangeResponse>builder()
                .message("Password changed successfully. All sessions have been revoked.")
                .result(response).build();
    }

    @PostMapping("/password/forgot/request")
    public ApiResponse<OtpResponse> requestPasswordReset(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        controllerUtils.enrichWithClientInfo(request, httpRequest);
        OtpResponse response = accountService.requestPasswordReset(request);
        return ApiResponse.<OtpResponse>builder()
                .message("OTP has been sent to your email address").result(response).build();
    }

    @PostMapping("/password/forgot/verify")
    public ApiResponse<Void> verifyPasswordReset(
            @Valid @RequestBody VerifyPasswordResetRequest request,
            HttpServletRequest httpRequest) {
        controllerUtils.enrichWithClientInfo(request, httpRequest);
        accountService.verifyPasswordReset(request);
        return ApiResponse.<Void>builder()
                .message("Password reset successfully. Please login with your new password.").build();
    }

    @PostMapping("/email/change/request")
    public ApiResponse<OtpResponse> requestChangeEmail(
            @Valid @RequestBody RequestChangeEmailOtpRequest request,
            HttpServletRequest httpRequest) {
        controllerUtils.enrichWithClientInfo(request, httpRequest);
        OtpResponse response = accountService.requestChangeEmail(controllerUtils.getCurrentUserId(), request);
        return ApiResponse.<OtpResponse>builder()
                .message("OTP has been sent to your current email for verification").result(response).build();
    }

    @PostMapping("/email/change")
    public ApiResponse<EmailChangeResponse> changeEmail(
            @Valid @RequestBody ChangeEmailRequest request,
            HttpServletRequest httpRequest) {
        controllerUtils.enrichWithClientInfo(request, httpRequest);
        EmailChangeResponse response = accountService.changeEmail(controllerUtils.getCurrentUserId(), request);
        return ApiResponse.<EmailChangeResponse>builder()
                .message("Email changed successfully. Please login again.").result(response).build();
    }

    @PostMapping("/phone/change/request")
    public ApiResponse<OtpResponse> requestChangePhone(
            @Valid @RequestBody RequestChangePhoneOtpRequest request,
            HttpServletRequest httpRequest) {
        controllerUtils.enrichWithClientInfo(request, httpRequest);
        OtpResponse response = accountService.requestChangePhone(controllerUtils.getCurrentUserId(), request);
        return ApiResponse.<OtpResponse>builder()
                .message("OTP has been sent to your email for verification").result(response).build();
    }

    @PostMapping("/phone/change")
    public ApiResponse<PhoneChangeResponse> changePhone(
            @Valid @RequestBody ChangePhoneRequest request,
            HttpServletRequest httpRequest) {
        controllerUtils.enrichWithClientInfo(request, httpRequest);
        PhoneChangeResponse response = accountService.changePhone(controllerUtils.getCurrentUserId(), request);
        return ApiResponse.<PhoneChangeResponse>builder()
                .message("Phone changed successfully. Please login again.").result(response).build();
    }

    @PostMapping("/delete/request")
    public ApiResponse<OtpResponse> requestDeleteAccount(
            @Valid @RequestBody RequestDeleteAccountOtpRequest request,
            HttpServletRequest httpRequest) {
        controllerUtils.enrichWithClientInfo(request, httpRequest);
        OtpResponse response = accountService.requestDeleteAccount(controllerUtils.getCurrentUserId(), request);
        return ApiResponse.<OtpResponse>builder()
                .message("OTP has been sent to your email to confirm account deletion").result(response).build();
    }

    @DeleteMapping
    public ApiResponse<AccountDeletionResponse> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request,
            HttpServletRequest httpRequest) {
        controllerUtils.enrichWithClientInfo(request, httpRequest);
        AccountDeletionResponse response = accountService.deleteAccount(controllerUtils.getCurrentUserId(), request);
        return ApiResponse.<AccountDeletionResponse>builder()
                .message("Account deleted successfully").result(response).build();
    }

    @PostMapping("/password/forgot/otp/verify")
    public ApiResponse<Void> verifyForgotPasswordOtp(
            @Valid @RequestBody VerifyForgotOtpRequest request) {
        accountService.verifyForgotPasswordOtp(request);
        return ApiResponse.<Void>builder().message("OTP verified").build();
    }
}