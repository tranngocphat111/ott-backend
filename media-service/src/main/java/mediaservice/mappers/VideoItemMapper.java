package mediaservice.mappers;

import mediaservice.dtos.requests.VideoItemRequest;
import mediaservice.dtos.responses.VideoItemResponse;
import mediaservice.models.VideoItem;
import mediaservice.utils.MediaUrlBuilder;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class VideoItemMapper {

    @Autowired
    protected MediaUrlBuilder mediaUrlBuilder;

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "story", ignore = true)
    public abstract VideoItem toEntity(VideoItemRequest request);

    public abstract VideoItemResponse toResponse(VideoItem videoItem);

    public abstract List<VideoItemResponse> toResponseList(List<VideoItem> videoItems);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    public abstract void updateEntity(VideoItemRequest request, @MappingTarget VideoItem videoItem);

    @AfterMapping
    protected void buildFullUrls(@MappingTarget VideoItemResponse response, VideoItem source) {
        if (source.getUrl() != null) {
            response.setUrl(convertToFullUrl(source.getUrl()));
        }
        if (source.getThumbnailUrl() != null) {
            response.setThumbnailUrl(convertToFullUrl(source.getThumbnailUrl()));
        }
    }

    private String convertToFullUrl(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }
        if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
            return relativePath;
        }
        return mediaUrlBuilder.buildS3Url("", relativePath);
    }
}
