package mediaservice.mappers;

import mediaservice.dtos.requests.TextItemRequest;
import mediaservice.dtos.responses.TextItemResponse;
import mediaservice.models.TextItem;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TextItemMapper {

    @Mapping(target = "id", ignore = true)
    TextItem toEntity(TextItemRequest request);

    TextItemResponse toResponse(TextItem textItem);

    List<TextItemResponse> toResponseList(List<TextItem> textItems);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(TextItemRequest request, @MappingTarget TextItem textItem);
}
