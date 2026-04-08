package iuh.fit.userservice.dto.response;

import iuh.fit.userservice.entity.enums.PhotoType;
import lombok.*;

import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserPhotoResponse {
    private String id;
    private String url;
    private String s3Key;
    private PhotoType photoType;
    private Boolean isActive;
    private LocalDateTime createdAt;
}