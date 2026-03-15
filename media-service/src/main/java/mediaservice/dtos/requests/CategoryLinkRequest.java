package mediaservice.dtos.requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.CategoryLinkTargetType;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategoryLinkRequest {
    private String categoryId;
    private String targetId;
    private CategoryLinkTargetType targetType;
}


