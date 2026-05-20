package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class PostResponse extends BaseContentResponse {
    private String caption;
    private List<MediaResponse> medias;
    private List<ContentAccessControlResponse> accessControls;
    private int totalReactions;
    private int totalComments;
    private int totalShares;
    private PostResponse sharedPost;
    private boolean sharedPostRestricted;
    private boolean sharedPostDeleted;
}

