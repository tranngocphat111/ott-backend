package iuh.fit.userservice.controller;

import iuh.fit.userservice.dto.request.AdminUserStatusRequest;
import iuh.fit.userservice.dto.response.AdminUserStatusResponse;
import iuh.fit.userservice.dto.response.ApiResponse;
import iuh.fit.userservice.service.AdminUserStatusService;
import iuh.fit.userservice.utils.ControllerUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserStatusService adminUserStatusService;
    private final ControllerUtils controllerUtils;

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminUserStatusResponse> updateUserStatus(
            @PathVariable String userId,
            @Valid @RequestBody AdminUserStatusRequest request) {
        String actorId = controllerUtils.getCurrentUserId();

        AdminUserStatusResponse response = adminUserStatusService.updateStatus(
                userId,
                request.getActionType(),
                request.getReason(),
                request.getDurationMinutes(),
                request.getIsPermanent(),
                actorId,
                "ADMIN"
        );

        return ApiResponse.<AdminUserStatusResponse>builder()
                .message("User status updated")
                .result(response)
                .build();
    }
}
