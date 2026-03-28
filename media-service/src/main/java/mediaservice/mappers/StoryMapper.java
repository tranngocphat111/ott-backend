package mediaservice.mappers;

import mediaservice.dtos.requests.StoryRequest;
import mediaservice.dtos.responses.StoryResponse;
import mediaservice.models.Story;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", uses = {StoryItemMapper.class})
public interface StoryMapper {

    @Mapping(target = "hashTags", ignore = true)
    @Mapping(target = "storyItems", source = "storyItems", qualifiedByName = "toEntitySet")
    @Mapping(target = "storyMusics", ignore = true)
    Story toEntity(StoryRequest request);

    @Mapping(target = "hashTags", ignore = true)
    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "accountUsername", source = "account.username")
    @Mapping(target = "accountDisplayName", source = "account.displayName")
    @Mapping(target = "accountAvatarUrl", source = "account.avatarUrl")
    @Mapping(target = "storyItems", source = "storyItems", qualifiedByName = "toResponseList")
    @Mapping(target = "musics", ignore = true)
    @Mapping(target = "totalViews", ignore = true)
    StoryResponse toResponse(Story story);

    List<StoryResponse> toResponseList(List<Story> stories);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "hashTags", ignore = true)
    @Mapping(target = "storyItems", source = "storyItems", qualifiedByName = "toEntitySet")
    @Mapping(target = "storyMusics", ignore = true)
    void updateEntity(StoryRequest request, @MappingTarget Story story);
}