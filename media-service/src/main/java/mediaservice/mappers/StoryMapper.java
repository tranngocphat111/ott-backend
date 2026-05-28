package mediaservice.mappers;

import mediaservice.dtos.requests.StoryRequest;
import mediaservice.dtos.responses.StoryResponse;
import mediaservice.models.Story;
import mediaservice.utils.MediaUrlBuilder;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring", uses = { StoryItemMapper.class, ContentAccessControlMapper.class }, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class StoryMapper {

    @Autowired
    protected MediaUrlBuilder mediaUrlBuilder;

    @Mapping(target = "hashTags", ignore = true)
    @Mapping(target = "storyItems", source = "storyItems", qualifiedByName = "toEntitySet")
    @Mapping(target = "storyMusics", ignore = true)
    public abstract Story toEntity(StoryRequest request);

    @Mapping(target = "hashTags", ignore = true)
    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "accountUsername", source = "account.username")
    @Mapping(target = "accountDisplayName", source = "account.displayName")
    @Mapping(target = "accountAvatarUrl", source = "account.avatarUrl")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "storyItems", source = "storyItems", qualifiedByName = "toResponseList")
    @Mapping(target = "musics", ignore = true)
    @Mapping(target = "totalViews", ignore = true)
    public abstract StoryResponse toResponse(Story story);

    public abstract List<StoryResponse> toResponseList(List<Story> stories);

    @AfterMapping
    protected void buildFullUrls(Story source, @MappingTarget StoryResponse response) {
        if (source.getAccount() != null && source.getAccount().getAvatarUrl() != null) {
            String relative = source.getAccount().getAvatarUrl();
            if (!relative.startsWith("http")) {
                response.setAccountAvatarUrl(mediaUrlBuilder.buildS3Url("", relative));
            }
        }
    }

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "hashTags", ignore = true)
    @Mapping(target = "storyItems", ignore = true)
    @Mapping(target = "storyMusics", ignore = true)
    public abstract void updateEntity(StoryRequest request, @MappingTarget Story story);
}