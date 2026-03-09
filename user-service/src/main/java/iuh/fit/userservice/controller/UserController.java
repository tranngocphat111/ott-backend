package iuh.fit.userservice.controller;

import iuh.fit.userservice.dto.request.RegisterRequest;
import iuh.fit.userservice.dto.request.RequestRegisterOtpRequest;
import iuh.fit.userservice.dto.response.ApiResponse;
import iuh.fit.userservice.dto.response.OtpResponse;
import iuh.fit.userservice.dto.response.UserResponse;
import iuh.fit.userservice.service.UserService;
import iuh.fit.userservice.utils.ControllerUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ControllerUtils controllerUtils;

    @PostMapping("/register/otp")
    public ApiResponse<OtpResponse> requestRegisterOtp(
            @Valid @RequestBody RequestRegisterOtpRequest request,
            HttpServletRequest httpRequest) {
        controllerUtils.enrichWithClientInfo(request, httpRequest);
        OtpResponse response = userService.requestRegisterOtp(request);
        return ApiResponse.<OtpResponse>builder()
                .message("OTP has been sent to your email")
                .result(response)
                .build();
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        controllerUtils.enrichWithClientInfo(request, httpRequest);
        UserResponse response = userService.register(request);
        return ApiResponse.<UserResponse>builder()
                .code(HttpStatus.CREATED.value())
                .message("Account created successfully. Please login to continue.")
                .result(response)
                .build();
    }
}