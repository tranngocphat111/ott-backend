package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.CategoryLinkTargetType;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategoryLinkResponse {
    private String id;
    private String categoryId;
    private String categoryName;
    private String targetId;
    private CategoryLinkTargetType targetType;
    private LocalDateTime createdAt;
}

