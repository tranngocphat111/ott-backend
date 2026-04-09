package mediaservice.mappers;

import mediaservice.dtos.requests.ImageItemRequest;
import mediaservice.dtos.responses.ImageItemResponse;
import mediaservice.models.ImageItem;
import mediaservice.utils.MediaUrlBuilder;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class ImageItemMapper {

    @Autowired
    protected MediaUrlBuilder mediaUrlBuilder;

    @Mapping(target = "id", ignore = true)
    public abstract ImageItem toEntity(ImageItemRequest request);

    public abstract ImageItemResponse toResponse(ImageItem imageItem);

    public abstract List<ImageItemResponse> toResponseList(List<ImageItem> imageItems);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    public abstract void updateEntity(ImageItemRequest request, @MappingTarget ImageItem imageItem);

    @AfterMapping
    protected void buildFullUrls(@MappingTarget ImageItemResponse response, ImageItem source) {
        if (source.getUrl() != null) {
            response.setUrl(convertToFullUrl(source.getUrl()));
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
