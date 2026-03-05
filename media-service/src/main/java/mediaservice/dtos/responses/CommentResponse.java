package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentResponse {
    private String id;
    private String text;
    private String accountId;
    private String accountUsername;
    private String accountDisplayName;
    private String accountAvatarUrl;
    private String parentCommentId;
    private boolean edited;
    private boolean deleted;
    private int depth;
    private int totalReplies;
    private int totalReactions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

