package iuh.fit.userservice.dto.request;

import iuh.fit.userservice.entity.enums.PhotoType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddPhotoRequest {
    @NotBlank(message = "fileUrl is required")
    private String fileUrl;

    @NotBlank(message = "s3Key is required")
    private String s3Key;

    private PhotoType photoType; // AVATAR | COVER
}