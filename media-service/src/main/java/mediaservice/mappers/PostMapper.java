package mediaservice.mappers;

import mediaservice.dtos.requests.PostRequest;
import mediaservice.dtos.responses.PostResponse;
import mediaservice.models.Post;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PostMapper {

    @Mapping(target = "hashTags", ignore = true)
    Post toEntity(PostRequest request);

    @Mapping(target = "hashTags", ignore = true)
    PostResponse toResponse(Post post);

    List<PostResponse> toResponseList(List<Post> posts);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "hashTags", ignore = true)
    void updateEntity(PostRequest request, @MappingTarget Post post);
}
