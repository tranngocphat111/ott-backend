package mediaservice.mappers;

import mediaservice.dtos.requests.CommentRequest;
import mediaservice.dtos.responses.CommentResponse;
import mediaservice.models.Comment;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CommentMapper {

    Comment toEntity(CommentRequest request);

    CommentResponse toResponse(Comment comment);

    List<CommentResponse> toResponseList(List<Comment> comments);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(CommentRequest request, @MappingTarget Comment comment);
}
