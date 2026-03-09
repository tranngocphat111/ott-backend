package iuh.fit.userservice.controller;

import iuh.fit.userservice.dto.request.LinkEmailRequest;
import iuh.fit.userservice.dto.request.LinkPhoneRequest;
import iuh.fit.userservice.dto.response.ApiResponse;
import iuh.fit.userservice.dto.response.UserResponse;
import iuh.fit.userservice.service.LinkingService;
import iuh.fit.userservice.utils.ControllerUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/link")
@RequiredArgsConstructor
public class LinkingController {

    private final LinkingService linkingService;
    private final ControllerUtils controllerUtils;

    @PostMapping("/phone")
    public ApiResponse<UserResponse> linkPhone(@Valid @RequestBody LinkPhoneRequest request) {
        UserResponse response = linkingService.linkPhoneNumber(controllerUtils.getCurrentUserId(), request);
        return ApiResponse.<UserResponse>builder()
                .message("Phone number linked successfully").result(response).build();
    }

    @PostMapping("/email")
    public ApiResponse<UserResponse> linkEmail(@Valid @RequestBody LinkEmailRequest request) {
        UserResponse response = linkingService.linkEmail(controllerUtils.getCurrentUserId(), request);
        return ApiResponse.<UserResponse>builder()
                .message("Email linked successfully").result(response).build();
    }
}