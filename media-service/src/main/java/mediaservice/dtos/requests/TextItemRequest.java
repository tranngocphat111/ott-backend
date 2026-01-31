package mediaservice.dtos.requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class TextItemRequest extends BaseStoryItemRequest {
    private String content;
    private String font;
    private String color;
    private String backgroundColor;
    private String alignment;
}

