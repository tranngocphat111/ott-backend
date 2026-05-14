package mediaservice.mappers;

import mediaservice.dtos.requests.StoryItemRequest;
import mediaservice.dtos.responses.StoryItemResponse;
import mediaservice.models.ImageItem;
import mediaservice.models.StoryItem;
import mediaservice.models.TextItem;
import mediaservice.models.VideoItem;
import org.mapstruct.Mapper;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {ImageItemMapper.class, VideoItemMapper.class, TextItemMapper.class}, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class StoryItemMapper {

    @Autowired
    protected ImageItemMapper imageItemMapper;

    @Autowired
    protected VideoItemMapper videoItemMapper;

    @Autowired
    protected TextItemMapper textItemMapper;

    public StoryItem toEntity(StoryItemRequest request) {
        if (request == null || request.getType() == null) return null;
        StoryItem item = switch (request.getType()) {
            case IMAGE_ITEM -> imageItemMapper.toEntity(request.getImageItem());
            case VIDEO_ITEM -> videoItemMapper.toEntity(request.getVideoItem());
            case TEXT_ITEM  -> textItemMapper.toEntity(request.getTextItem());
        };
        if (item != null) {
            item.setPrimary(request.isPrimary());
            item.setZIndex(request.getZIndex());
            item.setPositionX(request.getPositionX());
            item.setPositionY(request.getPositionY());
            item.setRotation(request.getRotation());
            item.setScale(request.getScale());
            item.setStartTime(request.getStartTime());
            item.setEndTime(request.getEndTime());
        }
        return item;
    }

    public StoryItemResponse toResponse(StoryItem storyItem) {
        if (storyItem == null) return null;
        StoryItemResponse response = new StoryItemResponse();
        if (storyItem instanceof ImageItem imageItem) {
            response.setType(mediaservice.models.enums.StoryItemType.IMAGE_ITEM);
            response.setImageItem(imageItemMapper.toResponse(imageItem));
        } else if (storyItem instanceof VideoItem videoItem) {
            response.setType(mediaservice.models.enums.StoryItemType.VIDEO_ITEM);
            response.setVideoItem(videoItemMapper.toResponse(videoItem));
        } else if (storyItem instanceof TextItem textItem) {
            response.setType(mediaservice.models.enums.StoryItemType.TEXT_ITEM);
            response.setTextItem(textItemMapper.toResponse(textItem));
        }
        
        response.setPrimary(storyItem.isPrimary());
        response.setZIndex(storyItem.getZIndex());
        response.setPositionX(storyItem.getPositionX());
        response.setPositionY(storyItem.getPositionY());
        response.setRotation(storyItem.getRotation());
        response.setScale(storyItem.getScale());
        response.setStartTime(storyItem.getStartTime());
        response.setEndTime(storyItem.getEndTime());
        
        return response;
    }

    @Named("toEntitySet")
    public Set<StoryItem> toEntitySet(List<StoryItemRequest> requests) {
        if (requests == null) return new HashSet<>();
        return requests.stream()
                .map(this::toEntity)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Named("toResponseList")
    public List<StoryItemResponse> toResponseList(Set<StoryItem> storyItems) {
        if (storyItems == null) return List.of();
        return storyItems.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}
