package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.User;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {
    private String id;
    private String content;
    private User user;
    private String visibility;
    private Map<String, Object> metadata;
    private int totalReactionsCount;
    private int commentsCount;
    private int shareCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
