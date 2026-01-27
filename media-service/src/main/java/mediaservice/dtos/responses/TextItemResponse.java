package mediaservice.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class TextItemResponse extends BaseStoryItemResponse {
    private String content;
    private String font;
    private String color;
    private String backgroundColor;
    private String alignment;
}

