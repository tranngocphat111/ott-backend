package mediaservice.dtos.requests;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Request DTO dùng cho cả create lẫn PATCH update.
 * Không có @AllArgsConstructor: Jackson sử dụng constructor rỗng (@NoArgsConstructor)
 * + setters, nên không bao giờ inject null vào field vắng mặt trong JSON.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class UserAccountRequest extends BaseAccountRequest {
    /**
     * Boolean wrapper (không phải primitive boolean) – cho phép null khi field
     * vắng trong JSON PATCH, giúp NullValuePropertyMappingStrategy.IGNORE bỏ qua.
     */
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean isCreator;
    private String work;
    private String location;
    private String relationshipStatus;
}

