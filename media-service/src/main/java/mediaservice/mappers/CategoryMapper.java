package mediaservice.mappers;

import mediaservice.dtos.requests.CategoryRequest;
import mediaservice.dtos.responses.CategoryResponse;
import mediaservice.models.Category;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    Category toEntity(CategoryRequest request);

    CategoryResponse toResponse(Category category);

    List<CategoryResponse> toResponseList(List<Category> categories);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(CategoryRequest request, @MappingTarget Category category);
}
