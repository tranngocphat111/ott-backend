package mediaservice.mappers;

import mediaservice.dtos.requests.HashTagRequest;
import mediaservice.dtos.responses.HashTagResponse;
import mediaservice.models.HashTag;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface HashTagMapper {

    HashTag toEntity(HashTagRequest request);

    HashTagResponse toResponse(HashTag hashTag);

    List<HashTagResponse> toResponseList(List<HashTag> hashTags);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(HashTagRequest request, @MappingTarget HashTag hashTag);
}
