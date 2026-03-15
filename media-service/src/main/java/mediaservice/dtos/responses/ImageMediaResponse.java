package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ImageMediaResponse {
    private String id;
    private String url;
    private String caption;
    private int orderIndex;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

