package mediaservice.mappers;

import mediaservice.dtos.requests.VideoItemRequest;
import mediaservice.dtos.responses.VideoItemResponse;
import mediaservice.models.VideoItem;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface VideoItemMapper {

    VideoItem toEntity(VideoItemRequest request);

    VideoItemResponse toResponse(VideoItem videoItem);

    List<VideoItemResponse> toResponseList(List<VideoItem> videoItems);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(VideoItemRequest request, @MappingTarget VideoItem videoItem);
}
