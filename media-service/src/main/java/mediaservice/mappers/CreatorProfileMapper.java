package mediaservice.mappers;

import mediaservice.dtos.requests.CreatorProfileRequest;
import mediaservice.dtos.responses.CreatorProfileResponse;
import mediaservice.models.CreatorProfile;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CreatorProfileMapper {

    CreatorProfile toEntity(CreatorProfileRequest request);

    CreatorProfileResponse toResponse(CreatorProfile creatorProfile);

    List<CreatorProfileResponse> toResponseList(List<CreatorProfile> creatorProfiles);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(CreatorProfileRequest request, @MappingTarget CreatorProfile creatorProfile);
}
