package mediaservice.mappers;

import mediaservice.dtos.requests.CommentRequest;
import mediaservice.dtos.responses.CommentResponse;
import mediaservice.models.Comment;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CommentMapper {

    @Mapping(target = "accountId",          source = "account.id")
    @Mapping(target = "accountUsername",     source = "account.username")
    @Mapping(target = "accountDisplayName",  source = "account.displayName")
    @Mapping(target = "accountAvatarUrl",    source = "account.avatarUrl")
    @Mapping(target = "parentCommentId",     source = "parentComment.id")
    @Mapping(target = "totalReplies",        expression = "java(comment.getChildCommentSet() != null ? comment.getChildCommentSet().size() : 0)")
    @Mapping(target = "edited",              source = "edited")
    @Mapping(target = "deleted",             source = "deleted")
    @Mapping(target = "totalReactions",      ignore = true)
    @Mapping(target = "createdAt", source = "createdAt", dateFormat = "yyyy-MM-dd'T'HH:mm:ss")
    @Mapping(target = "updatedAt", source = "updatedAt", dateFormat = "yyyy-MM-dd'T'HH:mm:ss")
    CommentResponse toResponse(Comment comment);

    List<CommentResponse> toResponseList(List<Comment> comments);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(CommentRequest request, @MappingTarget Comment comment);
}
