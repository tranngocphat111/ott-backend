package mediaservice.mappers;

import mediaservice.dtos.requests.RelationshipRequest;
import mediaservice.dtos.responses.RelationshipResponse;
import mediaservice.models.Relationship;
import mediaservice.utils.MediaUrlBuilder;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class RelationshipMapper {

    @Autowired
    protected MediaUrlBuilder mediaUrlBuilder;

    @Mapping(target = "requester", ignore = true)
    @Mapping(target = "receiver", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "acceptedAt", ignore = true)
    public abstract Relationship toEntity(RelationshipRequest request);

    @Mapping(source = "requester.id", target = "requesterId")
    @Mapping(source = "requester.username", target = "requesterUsername")
    @Mapping(source = "requester.displayName", target = "requesterDisplayName")
    @Mapping(source = "requester.avatarUrl", target = "requesterAvatarUrl")
    @Mapping(source = "receiver.id", target = "receiverId")
    @Mapping(source = "receiver.username", target = "receiverUsername")
    @Mapping(source = "receiver.displayName", target = "receiverDisplayName")
    @Mapping(source = "receiver.avatarUrl", target = "receiverAvatarUrl")
    public abstract RelationshipResponse toResponse(Relationship relationship);

    public abstract List<RelationshipResponse> toResponseList(List<Relationship> relationships);

    @AfterMapping
    protected void buildFullUrls(Relationship source, @MappingTarget RelationshipResponse response) {
        if (source.getRequester() != null && source.getRequester().getAvatarUrl() != null) {
            String relative = source.getRequester().getAvatarUrl();
            if (!relative.startsWith("http")) {
                response.setRequesterAvatarUrl(mediaUrlBuilder.buildS3Url("", relative));
            }
        }
        if (source.getReceiver() != null && source.getReceiver().getAvatarUrl() != null) {
            String relative = source.getReceiver().getAvatarUrl();
            if (!relative.startsWith("http")) {
                response.setReceiverAvatarUrl(mediaUrlBuilder.buildS3Url("", relative));
            }
        }
    }

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "requester", ignore = true)
    @Mapping(target = "receiver", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "acceptedAt", ignore = true)
    public abstract void updateEntity(RelationshipRequest request, @MappingTarget Relationship relationship);
}
