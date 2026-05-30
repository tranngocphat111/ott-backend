package mediaservice.controllers;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.UserAccountRequest;
import mediaservice.dtos.responses.UserAccountResponse;
import mediaservice.services.UserAccountService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
     * GET /users/search?q=...&page=&size=
     * Tìm user theo họ tên, username, email, hoặc số điện thoại.
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(
            @RequestParam String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null && size != null) {
            Page<UserAccountResponse> results = userAccountService.searchUserAccounts(q, PageRequest.of(page, size));
            return ResponseEntity.ok(results);
        }
        return ResponseEntity.ok(userAccountService.searchUserAccounts(q));
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
