package iuh.fit.ottbackend.controller;

import iuh.fit.ottbackend.dto.request.LinkEmailRequest;
import iuh.fit.ottbackend.dto.request.LinkPhoneRequest;
import iuh.fit.ottbackend.dto.response.ApiResponse;
import iuh.fit.ottbackend.dto.response.UserResponse;
import iuh.fit.ottbackend.service.LinkingService;
import iuh.fit.ottbackend.utils.ControllerUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/linking")
@RequiredArgsConstructor
public class LinkingController {

    private final LinkingService linkingService;
    private final ControllerUtils controllerUtils;

    @PostMapping("/phone")
    public ApiResponse<UserResponse> linkPhoneNumber(
            @Valid @RequestBody LinkPhoneRequest request,
            HttpServletRequest httpRequest) {

        String userId = controllerUtils.getCurrentUserId();
        controllerUtils.enrichWithClientInfo(request, httpRequest);

        UserResponse response = linkingService.linkPhoneNumber(userId, request);

        return ApiResponse.<UserResponse>builder()
                .message("Phone number linked successfully")
                .result(response)
                .build();
    }

    @PostMapping("/email")
    public ApiResponse<UserResponse> linkEmail(
            @Valid @RequestBody LinkEmailRequest request,
            HttpServletRequest httpRequest) {

        String userId = controllerUtils.getCurrentUserId();
        controllerUtils.enrichWithClientInfo(request, httpRequest);

        UserResponse response = linkingService.linkEmail(userId, request);

        return ApiResponse.<UserResponse>builder()
                .message("Email linked successfully")
                .result(response)
                .build();
    }
}