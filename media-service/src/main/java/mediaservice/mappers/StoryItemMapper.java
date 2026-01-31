package mediaservice.mappers;

import mediaservice.models.StoryItem;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {ImageItemMapper.class, VideoItemMapper.class, TextItemMapper.class})
public interface StoryItemMapper {
    // Composite mapper for StoryItem polymorphic handling
}

