package mediaservice.dtos.requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentRequest {
    private String text;
    private String accountId;
    private String contentId;  // ID of the content being commented on
    private String parentCommentId;  // For nested comments
}

