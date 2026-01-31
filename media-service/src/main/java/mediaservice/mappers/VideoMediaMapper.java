package mediaservice.mappers;

import mediaservice.dtos.requests.VideoMediaRequest;
import mediaservice.dtos.responses.VideoMediaResponse;
import mediaservice.models.VideoMedia;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface VideoMediaMapper {

    VideoMedia toEntity(VideoMediaRequest request);

    VideoMediaResponse toResponse(VideoMedia videoMedia);

    List<VideoMediaResponse> toResponseList(List<VideoMedia> videoMedias);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(VideoMediaRequest request, @MappingTarget VideoMedia videoMedia);
}
