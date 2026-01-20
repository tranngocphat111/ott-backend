package iuh.fit.ottbackend.controller;

import com.nimbusds.jose.JOSEException;
import iuh.fit.ottbackend.dto.request.*;
import iuh.fit.ottbackend.dto.response.ApiResponse;
import iuh.fit.ottbackend.dto.response.AuthenticationResponse;
import iuh.fit.ottbackend.dto.response.IntrospectResponse;
import iuh.fit.ottbackend.dto.response.OtpResponse;
import iuh.fit.ottbackend.entity.enums.DeviceType;
import iuh.fit.ottbackend.service.AuthService;
import iuh.fit.ottbackend.service.JwtService;
import iuh.fit.ottbackend.utils.ControllerUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final ControllerUtils controllerUtils;

    @PostMapping("/login/local")
    public ApiResponse<AuthenticationResponse> localLogin(
            @Valid @RequestBody LocalLoginRequest request,
            HttpServletRequest httpRequest) {

        controllerUtils.enrichWithClientInfo(request, httpRequest);
        AuthenticationResponse response = authService.localLogin(request);

        if (response.isRequires2FA()) {
            return ApiResponse.<AuthenticationResponse>builder()
                    .result(response)
                    .message("Two-factor authentication required. Please enter OTP sent to your email.")
                    .build();
        }

        return ApiResponse.<AuthenticationResponse>builder()
                .result(response)
                .message("Login successful")
                .build();
    }

    @PostMapping("/login/google")
    public ApiResponse<AuthenticationResponse> googleLogin(
            @Valid @RequestBody GoogleAuthRequest request,
            HttpServletRequest httpRequest) {

        controllerUtils.enrichWithClientInfo(request, httpRequest);
        AuthenticationResponse response = authService.googleAuth(request);

        if (response.isRequiresPhoneSetup()) {
            return ApiResponse.<AuthenticationResponse>builder()
                    .result(response)
                    .message("Please provide your phone number to complete registration")
                    .build();
        }

        if (response.isRequires2FA()) {
            return ApiResponse.<AuthenticationResponse>builder()
                    .result(response)
                    .message("Two-factor authentication required. Please enter OTP sent to your email.")
                    .build();
        }

        return ApiResponse.<AuthenticationResponse>builder()
                .result(response)
                .message("Google login successful")
                .build();
    }

    @PostMapping("/login/google/complete")
    public ApiResponse<AuthenticationResponse> completeGoogleRegistration(
            @Valid @RequestBody CompleteGoogleRegistrationRequest request,
            HttpServletRequest httpRequest) {

        controllerUtils.enrichWithClientInfo(request, httpRequest);
        AuthenticationResponse response = authService.completeGoogleRegistration(request);

        return ApiResponse.<AuthenticationResponse>builder()
                .result(response)
                .message("Registration completed successfully")
                .build();
    }

    @PostMapping("/2fa/otp/request")
    public ApiResponse<OtpResponse> request2FAOtp(
            @Valid @RequestBody Request2FAOtpRequest request,
            HttpServletRequest httpRequest) {

        controllerUtils.enrichWithClientInfo(request, httpRequest);
        OtpResponse response = authService.request2FAOtp(request);

        return ApiResponse.<OtpResponse>builder()
                .message("OTP has been sent to your email")
                .result(response)
                .build();
    }

    @PostMapping("/2fa/verify")
    public ApiResponse<AuthenticationResponse> verify2FAOtp(
            @Valid @RequestBody Verify2FARequest request,
            HttpServletRequest httpRequest) {

        controllerUtils.enrichWithClientInfo(request, httpRequest);

        AuthenticationResponse response = authService.verify2FAOtp(
                request.getTempToken(),
                request.getOtpCode(),
                request.getDeviceId(),
                request.getDeviceType() != null ?
                        request.getDeviceType() : null,
                request.getIpAddress(),
                request.getDeviceInfo()
        );

        return ApiResponse.<AuthenticationResponse>builder()
                .result(response)
                .message("Two-factor authentication successful")
                .build();
    }

    @PostMapping("/introspect")
    public ApiResponse<IntrospectResponse> introspect(
            @Valid @RequestBody IntrospectRequest request)
            throws ParseException, JOSEException {

        IntrospectResponse response = jwtService.introspect(request);

        return ApiResponse.<IntrospectResponse>builder()
                .result(response)
                .build();
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthenticationResponse> refresh(
            @Valid @RequestBody RefreshRequest request)
            throws ParseException, JOSEException {

        AuthenticationResponse response = authService.refreshToken(request);

        return ApiResponse.<AuthenticationResponse>builder()
                .result(response)
                .message("Token refreshed successfully")
                .build();
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request)
            throws ParseException, JOSEException {

        authService.logout(request);

        return ApiResponse.<Void>builder()
                .message("Logout successful")
                .build();
    }
}