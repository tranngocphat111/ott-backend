package mediaservice.mappers;

import mediaservice.dtos.requests.StoryRequest;
import mediaservice.dtos.responses.StoryResponse;
import mediaservice.models.Story;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface StoryMapper {

    @Mapping(target = "hashTags", ignore = true)
    Story toEntity(StoryRequest request);

    @Mapping(target = "hashTags", ignore = true)
    StoryResponse toResponse(Story story);

    List<StoryResponse> toResponseList(List<Story> stories);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "hashTags", ignore = true)
    void updateEntity(StoryRequest request, @MappingTarget Story story);
}