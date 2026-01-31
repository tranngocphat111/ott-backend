package mediaservice.mappers;

import mediaservice.dtos.requests.ImageItemRequest;
import mediaservice.dtos.responses.ImageItemResponse;
import mediaservice.models.ImageItem;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ImageItemMapper {

    ImageItem toEntity(ImageItemRequest request);

    ImageItemResponse toResponse(ImageItem imageItem);

    List<ImageItemResponse> toResponseList(List<ImageItem> imageItems);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(ImageItemRequest request, @MappingTarget ImageItem imageItem);
}
