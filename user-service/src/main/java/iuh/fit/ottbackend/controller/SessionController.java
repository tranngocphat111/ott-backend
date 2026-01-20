package iuh.fit.ottbackend.controller;

import iuh.fit.ottbackend.dto.response.ApiResponse;
import iuh.fit.ottbackend.dto.response.UserSessionsResponse;
import iuh.fit.ottbackend.service.SessionService;
import iuh.fit.ottbackend.utils.ControllerUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

        return ApiResponse.<UserSessionsResponse>builder()
                .result(response)
                .build();
    }

    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> revokeSession(@PathVariable String sessionId) {
        String userId = controllerUtils.getCurrentUserId();
        sessionService.revokeSession(userId, sessionId);

        return ApiResponse.<Void>builder()
                .message("Session revoked successfully")
                .build();
    }

    @DeleteMapping("/others")
    public ApiResponse<Void> revokeAllOtherSessions() {
        String userId = controllerUtils.getCurrentUserId();
        String currentToken = controllerUtils.getCurrentSessionToken();

        sessionService.revokeAllOtherSessions(userId, currentToken);

        return ApiResponse.<Void>builder()
                .message("All other sessions revoked successfully. Only your current session remains active.")
                .build();
    }

    @DeleteMapping("/all")
    public ApiResponse<Void> revokeAllSessions() {
        String userId = controllerUtils.getCurrentUserId();

        int revokedCount = sessionService.revokeAllUserSessions(
                userId,
                "Revoked by user - logout from all devices"
        );

        return ApiResponse.<Void>builder()
                .message(String.format("All %d sessions revoked successfully. You have been logged out from all devices.", revokedCount))
                .build();
    }
}