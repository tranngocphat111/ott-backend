package mediaservice.mappers;

import mediaservice.dtos.requests.ImageMediaRequest;
import mediaservice.dtos.responses.ImageMediaResponse;
import mediaservice.models.ImageMedia;
import mediaservice.utils.MediaUrlBuilder;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class ImageMediaMapper {

    @Autowired
    protected MediaUrlBuilder mediaUrlBuilder;

    public abstract ImageMedia toEntity(ImageMediaRequest request);

    public abstract ImageMediaResponse toResponse(ImageMedia imageMedia);

    public abstract List<ImageMediaResponse> toResponseList(List<ImageMedia> imageMedias);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    public abstract void updateEntity(ImageMediaRequest request, @MappingTarget ImageMedia imageMedia);

    /**
     * Post-process response to build full URL
     */
    @AfterMapping
    protected void buildFullUrls(@MappingTarget ImageMediaResponse response, ImageMedia source) {
        if (source.getUrl() != null) {
            response.setUrl(convertToFullUrl(source.getUrl()));
        }
    }

    /**
     * Build full URL from relative path stored in database
     */
    private String convertToFullUrl(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }

        // If already a full URL, return as-is (for backward compatibility)
        if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
            return relativePath;
        }

        // Build full URL using MediaUrlBuilder
        return mediaUrlBuilder.buildS3Url("", relativePath);
    }
}
