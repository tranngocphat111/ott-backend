package mediaservice.dtos.requests;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Không có @AllArgsConstructor: Jackson sử dụng no-arg constructor + setters.
 * Tránh lỗi "Cannot map null into type boolean" khi các field vắng trong JSON.
 */
@Data
@NoArgsConstructor
public abstract class BaseAccountRequest {
    private String username;
    private String displayName;
    private String email;
    private String avatarUrl;
    private String coverUrl;
    private String phoneNumber;
    private String bio;
}

