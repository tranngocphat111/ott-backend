package mediaservice.mappers;

import mediaservice.dtos.requests.FollowRequest;
import mediaservice.dtos.responses.FollowResponse;
import mediaservice.models.Follow;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface FollowMapper {

    Follow toEntity(FollowRequest request);

    FollowResponse toResponse(Follow follow);

    List<FollowResponse> toResponseList(List<Follow> follows);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(FollowRequest request, @MappingTarget Follow follow);
}
