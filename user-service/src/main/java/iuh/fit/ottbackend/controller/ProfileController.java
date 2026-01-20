package iuh.fit.ottbackend.controller;

import iuh.fit.ottbackend.dto.request.UpdateProfileRequest;
import iuh.fit.ottbackend.dto.response.ApiResponse;
import iuh.fit.ottbackend.dto.response.UserProfileResponse;
import iuh.fit.ottbackend.service.ProfileService;
import iuh.fit.ottbackend.utils.ControllerUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final ControllerUtils controllerUtils;

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getCurrentUserProfile() {
        String userId = controllerUtils.getCurrentUserId();
        UserProfileResponse response = profileService.getUserProfile(userId);

        return ApiResponse.<UserProfileResponse>builder()
                .result(response)
                .build();
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserProfileResponse> getUserProfile(@PathVariable String userId) {
        UserProfileResponse response = profileService.getUserProfile(userId);

        return ApiResponse.<UserProfileResponse>builder()
                .result(response)
                .build();
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