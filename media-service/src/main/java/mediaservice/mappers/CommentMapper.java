package mediaservice.mappers;

import mediaservice.dtos.requests.CommentRequest;
import mediaservice.dtos.responses.CommentResponse;
import mediaservice.models.Comment;
import mediaservice.utils.MediaUrlBuilder;
import org.mapstruct.*;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class CommentMapper {

    @Autowired
    protected MediaUrlBuilder mediaUrlBuilder;

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "accountUsername", source = "account.username")
    @Mapping(target = "accountDisplayName", source = "account.displayName")
    @Mapping(target = "accountAvatarUrl", source = "account.avatarUrl")
    @Mapping(target = "parentCommentId", source = "parentComment.id")
    @Mapping(target = "totalReplies", expression = "java(comment.getChildCommentSet() != null ? comment.getChildCommentSet().size() : 0)")
    @Mapping(target = "edited", source = "edited")
    @Mapping(target = "deleted", source = "deleted")
    @Mapping(target = "totalReactions", ignore = true)
    @Mapping(target = "createdAt", source = "createdAt", dateFormat = "yyyy-MM-dd'T'HH:mm:ss")
    @Mapping(target = "updatedAt", source = "updatedAt", dateFormat = "yyyy-MM-dd'T'HH:mm:ss")
    public abstract CommentResponse toResponse(Comment comment);

    public abstract List<CommentResponse> toResponseList(List<Comment> comments);

    @AfterMapping
    protected void buildFullUrls(Comment source, @MappingTarget CommentResponse response) {
        if (source.getAccount() != null && source.getAccount().getAvatarUrl() != null) {
            String relative = source.getAccount().getAvatarUrl();
            if (!relative.startsWith("http")) {
                response.setAccountAvatarUrl(mediaUrlBuilder.buildS3Url("", relative));
            }
        }
    }

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    public abstract void updateEntity(CommentRequest request, @MappingTarget Comment comment);
}
