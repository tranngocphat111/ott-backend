package iuh.fit.userservice.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PresignedUrlResponse {
    private String uploadUrl;
    private String fileUrl;
    private String s3Key;
    private int expiresInMinutes;
    private String contentType;
}