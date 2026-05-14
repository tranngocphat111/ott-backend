package mediaservice.dtos.messages;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaCompressionJob {
    private String tempPath;
    private String mediaType;
    private String s3Key;
    private String contentType;
    private String contentId;
    private String contentTargetType;
    private String operation;
    private String mediaId;
    private Integer orderIndex;
}
