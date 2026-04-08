package iuh.fit.userservice.controller;

import iuh.fit.userservice.dto.request.AddPhotoRequest;
import iuh.fit.userservice.dto.response.*;
import iuh.fit.userservice.entity.enums.PhotoType;
import iuh.fit.userservice.service.ProfileService;
import iuh.fit.userservice.service.UploadService;
import iuh.fit.userservice.service.UserPhotoService;
import iuh.fit.userservice.utils.ControllerUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/users/photos", "/internal/users/photos"})
@RequiredArgsConstructor
public class UserPhotoController {

    private final UserPhotoService userPhotoService;
    private final UploadService uploadService;
    private final ProfileService profileService;
    private final ControllerUtils controllerUtils;


    @GetMapping("/presigned-url")
    public ApiResponse<PresignedUrlResponse> getPresignedUrl(
            @RequestParam String filename,
            @RequestParam PhotoType type) {

        String userId = controllerUtils.getCurrentUserId();
        String folder = type == PhotoType.AVATAR ? "avatar" : "cover-photo";

        PresignedUrlResponse response = uploadService.generatePresignedUrl(userId, folder, filename);
        return ApiResponse.<PresignedUrlResponse>builder()
                .message("Presigned URL generated. Upload within 5 minutes.")
                .result(response).build();
    }

    // ─── 2. QUẢN LÝ BỘ SƯU TẬP ẢNH (GALLERY) ─────────────────────────────────

    @GetMapping
    public ApiResponse<PhotoListResponse> getAllPhotos() {
        String userId = controllerUtils.getCurrentUserId();
        return ApiResponse.<PhotoListResponse>builder()
                .message("Photos retrieved")
                .result(userPhotoService.getAllPhotos(userId)).build();
    }

    @PostMapping
    public ApiResponse<UserPhotoResponse> addPhotoToGallery(@Valid @RequestBody AddPhotoRequest request) {
        String userId = controllerUtils.getCurrentUserId();
        return ApiResponse.<UserPhotoResponse>builder()
                .message("Photo added to gallery")
                .result(userPhotoService.addPhoto(userId, request)).build();
    }

    @DeleteMapping("/{photoId}")
    public ApiResponse<Void> deletePhoto(@PathVariable String photoId) {
        String userId = controllerUtils.getCurrentUserId();
        userPhotoService.deletePhoto(userId, photoId);
        return ApiResponse.<Void>builder()
                .message("Photo deleted").build();
    }

    // ─── 3. THAY ĐỔI ẢNH ĐẠI DIỆN / ẢNH BÌA HIỆN TẠI (ACTIVE) ────────────────

    // TH 1: Đổi ảnh active từ một bức ảnh CÓ SẴN trong gallery
    @PatchMapping("/{photoId}/active")
    public ApiResponse<UserPhotoResponse> setActiveFromGallery(@PathVariable String photoId) {
        String userId = controllerUtils.getCurrentUserId();
        return ApiResponse.<UserPhotoResponse>builder()
                .message("Active photo updated")
                .result(userPhotoService.setActivePhoto(userId, photoId)).build();
    }

    // TH 2: Upload MỚI TINH và set làm Avatar luôn
    @PatchMapping("/avatar")
    public ApiResponse<UserProfileResponse> uploadAndSetActiveAvatar(@Valid @RequestBody AddPhotoRequest request) {
        String userId = controllerUtils.getCurrentUserId();
        request.setPhotoType(PhotoType.AVATAR); // Đảm bảo đúng type

        userPhotoService.addAndSetActive(userId, request);
        return ApiResponse.<UserProfileResponse>builder()
                .message("Avatar updated successfully")
                .result(profileService.getUserProfile(userId)).build();
    }

    // TH 3: Upload MỚI TINH và set làm Cover luôn
    @PatchMapping("/cover")
    public ApiResponse<UserProfileResponse> uploadAndSetActiveCover(@Valid @RequestBody AddPhotoRequest request) {
        String userId = controllerUtils.getCurrentUserId();
        request.setPhotoType(PhotoType.COVER); // Đảm bảo đúng type

        userPhotoService.addAndSetActive(userId, request);
        return ApiResponse.<UserProfileResponse>builder()
                .message("Cover updated successfully")
                .result(profileService.getUserProfile(userId)).build();
    }

    // ─── 4. GỠ BỎ ẢNH ĐANG DÙNG (RESET VỀ DEFAULT) ───────────────────────────

    @DeleteMapping("/avatar")
    public ApiResponse<UserProfileResponse> removeActiveAvatar() {
        String userId = controllerUtils.getCurrentUserId();
        userPhotoService.removeActivePhotoByType(userId, PhotoType.AVATAR);
        return ApiResponse.<UserProfileResponse>builder()
                .message("Avatar removed, reset to default")
                .result(profileService.getUserProfile(userId)).build();
    }

    @DeleteMapping("/cover")
    public ApiResponse<UserProfileResponse> removeActiveCover() {
        String userId = controllerUtils.getCurrentUserId();
        userPhotoService.removeActivePhotoByType(userId, PhotoType.COVER);
        return ApiResponse.<UserProfileResponse>builder()
                .message("Cover removed, reset to default")
                .result(profileService.getUserProfile(userId)).build();
    }
}