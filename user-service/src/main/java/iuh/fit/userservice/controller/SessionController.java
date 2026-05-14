package iuh.fit.userservice.controller;

import iuh.fit.userservice.dto.response.ApiResponse;
import iuh.fit.userservice.dto.response.UserSessionsResponse;
import iuh.fit.userservice.service.SessionService;
import iuh.fit.userservice.utils.ControllerUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final ControllerUtils controllerUtils;

    @GetMapping
    public ApiResponse<UserSessionsResponse> getUserSessions() {
        String userId = controllerUtils.getCurrentUserId();
        String currentToken = controllerUtils.getCurrentSessionToken();
        UserSessionsResponse response = sessionService.getUserSessions(userId, currentToken);
        return ApiResponse.<UserSessionsResponse>builder().result(response).build();
    }

    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> revokeSession(@PathVariable String sessionId) {
        sessionService.revokeSession(controllerUtils.getCurrentUserId(), sessionId);
        return ApiResponse.<Void>builder().message("Session revoked successfully").build();
    }

    @DeleteMapping("/others")
    public ApiResponse<Void> revokeAllOtherSessions() {
        String userId = controllerUtils.getCurrentUserId();
        String currentToken = controllerUtils.getCurrentSessionToken();
        sessionService.revokeAllOtherSessions(userId, currentToken);
        return ApiResponse.<Void>builder()
                .message("All other sessions revoked. Only your current session remains active.").build();
    }

    @DeleteMapping("/all")
    public ApiResponse<Void> revokeAllSessions() {
        int count = sessionService.revokeAllUserSessions(
                controllerUtils.getCurrentUserId(), "Revoked by user - logout from all devices");
        return ApiResponse.<Void>builder()
                .message(String.format("All %d sessions revoked. You have been logged out from all devices.", count))
                .build();
    }
}