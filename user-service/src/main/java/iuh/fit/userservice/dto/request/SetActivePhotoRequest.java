package iuh.fit.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SetActivePhotoRequest {
    @NotBlank(message = "photoId is required")
    private String photoId;
}