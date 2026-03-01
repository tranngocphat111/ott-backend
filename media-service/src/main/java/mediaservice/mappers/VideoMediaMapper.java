package mediaservice.mappers;

import mediaservice.dtos.requests.VideoMediaRequest;
import mediaservice.dtos.responses.VideoMediaResponse;
import mediaservice.models.VideoMedia;
import mediaservice.utils.MediaUrlBuilder;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class VideoMediaMapper {

    @Autowired
    protected MediaUrlBuilder mediaUrlBuilder;

    public abstract VideoMedia toEntity(VideoMediaRequest request);

    public abstract VideoMediaResponse toResponse(VideoMedia videoMedia);

    public abstract List<VideoMediaResponse> toResponseList(List<VideoMedia> videoMedias);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    public abstract void updateEntity(VideoMediaRequest request, @MappingTarget VideoMedia videoMedia);

    /**
     * Post-process response to build full URLs
     */
    @AfterMapping
    protected void buildFullUrls(@MappingTarget VideoMediaResponse response, VideoMedia source) {
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
