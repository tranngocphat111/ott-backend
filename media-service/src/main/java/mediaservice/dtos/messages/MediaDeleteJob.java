package mediaservice.dtos.messages;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaDeleteJob {
    private List<String> s3Keys;
    private String contentId;
    private String contentTargetType;
    private String operation;
}
