package mediaservice.controllers;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.UserAccountRequest;
import mediaservice.dtos.responses.UserAccountResponse;
import mediaservice.services.UserAccountService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserAccountController {

    private final UserAccountService userAccountService;

    /** GET /users – trả về tất cả user */
    @GetMapping
    public ResponseEntity<List<UserAccountResponse>> getAllUsers() {
        return ResponseEntity.ok(userAccountService.getAllUserAccounts());
    }

    /** GET /users/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<UserAccountResponse> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(userAccountService.getUserAccountById(id));
    }

    /** GET /users/username/{username} */
    @GetMapping("/username/{username}")
    public ResponseEntity<UserAccountResponse> getUserByUsername(@PathVariable String username) {
        return ResponseEntity.ok(userAccountService.getUserAccountByUsername(username));
    }

    /**
     * PATCH /users/{id}
     * Cập nhật thông tin giới thiệu của người dùng (bio, work, location, relationshipStatus,
     * displayName, avatarUrl, ...).
     * Chỉ cập nhật các trường không null nhờ NullValuePropertyMappingStrategy.IGNORE trong mapper.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<UserAccountResponse> updateUser(
            @PathVariable String id,
            @RequestBody UserAccountRequest request) {
        return ResponseEntity.ok(userAccountService.updateUserAccount(id, request));
    }

    /**
     * PATCH /users/{id}/avatar – upload ảnh đại diện lên S3 (folder: avatars).
     * Trả về UserAccountResponse với avatarUrl đã cập nhật.
     */
    @PatchMapping(value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserAccountResponse> uploadAvatar(
            @PathVariable String id,
            @RequestParam("avatar") MultipartFile file) {
        return ResponseEntity.ok(userAccountService.uploadAvatar(id, file));
    }

    /**
     * PATCH /users/{id}/cover – upload ảnh bìa lên S3 (folder: covers).
     * Trả về UserAccountResponse với coverUrl đã cập nhật.
     */
    @PatchMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserAccountResponse> uploadCover(
            @PathVariable String id,
            @RequestParam("cover") MultipartFile file) {
        return ResponseEntity.ok(userAccountService.uploadCover(id, file));
    }
}
