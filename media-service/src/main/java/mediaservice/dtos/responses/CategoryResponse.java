package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategoryResponse {
    private String id;
    private String name;
    private String description;
    private boolean isActive;
    private String parentCategoryId;
    private String parentCategoryName;
    private int totalContents;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
}

