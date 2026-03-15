package mediaservice.mappers;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {ImageMediaMapper.class, VideoMediaMapper.class})
public interface MediaMapper {
    // Helper mapper for Media polymorphic handling
    // Actual mapping should be done via ImageMediaMapper and VideoMediaMapper
}
