package iuh.fit.userservice.controller;

import iuh.fit.userservice.dto.request.AdminUserStatusRequest;
import iuh.fit.userservice.dto.response.AdminUserStatusResponse;
import iuh.fit.userservice.dto.response.ApiResponse;
import iuh.fit.userservice.service.AdminUserStatusService;
import iuh.fit.userservice.utils.ControllerUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserStatusService adminUserStatusService;
    private final ControllerUtils controllerUtils;

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MODERATOR')")
    public ApiResponse<AdminUserStatusResponse> updateUserStatus(
            @PathVariable String userId,
            @Valid @RequestBody AdminUserStatusRequest request) {
        String actorId = controllerUtils.getCurrentUserId();
        String actorRole = resolveActorRole();

        AdminUserStatusResponse response = adminUserStatusService.updateStatus(
                userId,
                request.getActionType(),
                request.getReason(),
                request.getDurationMinutes(),
                request.getIsPermanent(),
                actorId,
                actorRole
        );

        return ApiResponse.<AdminUserStatusResponse>builder()
                .message("User status updated")
                .result(response)
                .build();
    }

    private String resolveActorRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "UNKNOWN";
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        boolean isSuperAdmin = authorities.stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equalsIgnoreCase(a.getAuthority()));
        if (isSuperAdmin) return "SUPER_ADMIN";
        boolean isModerator = authorities.stream()
                .anyMatch(a -> "ROLE_MODERATOR".equalsIgnoreCase(a.getAuthority()));
        if (isModerator) return "MODERATOR";
        return "UNKNOWN";
    }
}
