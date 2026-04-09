package iuh.fit.userservice.controller;

import iuh.fit.userservice.dto.request.UpdateProfileRequest;
import iuh.fit.userservice.dto.response.ApiResponse;
import iuh.fit.userservice.dto.response.UserProfileResponse;
import iuh.fit.userservice.service.ProfileService;
import iuh.fit.userservice.utils.ControllerUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final ControllerUtils controllerUtils;

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getMyProfile() {
        String userId = controllerUtils.getCurrentUserId();
        UserProfileResponse response = profileService.getUserProfile(userId);
        return ApiResponse.<UserProfileResponse>builder().result(response).build();
    }

    // Public endpoint - xem profile người khác (avatar, tên, bio)
    @GetMapping("/{userId}")
    public ApiResponse<UserProfileResponse> getPublicProfile(@PathVariable String userId) {
        UserProfileResponse response = profileService.getPublicProfile(userId);
        return ApiResponse.<UserProfileResponse>builder().result(response).build();
    }

    @PutMapping("/me")
    public ApiResponse<UserProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        String userId = controllerUtils.getCurrentUserId();
        UserProfileResponse response = profileService.updateProfile(userId, request);
        return ApiResponse.<UserProfileResponse>builder()
                .message("Profile updated successfully")
                .result(response)
                .build();
    }
}