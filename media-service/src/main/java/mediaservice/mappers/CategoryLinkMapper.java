package mediaservice.mappers;

import mediaservice.dtos.requests.CategoryLinkRequest;
import mediaservice.dtos.responses.CategoryLinkResponse;
import mediaservice.models.CategoryLink;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CategoryLinkMapper {

    CategoryLink toEntity(CategoryLinkRequest request);

    CategoryLinkResponse toResponse(CategoryLink categoryLink);

    List<CategoryLinkResponse> toResponseList(List<CategoryLink> categoryLinks);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(CategoryLinkRequest request, @MappingTarget CategoryLink categoryLink);
}
