package mediaservice.mappers;

import mediaservice.dtos.requests.ImageMediaRequest;
import mediaservice.dtos.responses.ImageMediaResponse;
import mediaservice.models.ImageMedia;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ImageMediaMapper {

    ImageMedia toEntity(ImageMediaRequest request);

    ImageMediaResponse toResponse(ImageMedia imageMedia);

    List<ImageMediaResponse> toResponseList(List<ImageMedia> imageMedias);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(ImageMediaRequest request, @MappingTarget ImageMedia imageMedia);
}
