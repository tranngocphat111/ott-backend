package mediaservice.mappers;

import mediaservice.dtos.requests.RelationshipRequest;
import mediaservice.dtos.responses.RelationshipResponse;
import mediaservice.models.Relationship;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RelationshipMapper {

    @Mapping(target = "requester", ignore = true)
    @Mapping(target = "receiver", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "acceptedAt", ignore = true)
    Relationship toEntity(RelationshipRequest request);

    @Mapping(source = "requester.id",        target = "requesterId")
    @Mapping(source = "requester.username",   target = "requesterUsername")
    @Mapping(source = "requester.displayName", target = "requesterDisplayName")
    @Mapping(source = "requester.avatarUrl",  target = "requesterAvatarUrl")
    @Mapping(source = "receiver.id",          target = "receiverId")
    @Mapping(source = "receiver.username",    target = "receiverUsername")
    @Mapping(source = "receiver.displayName", target = "receiverDisplayName")
    @Mapping(source = "receiver.avatarUrl",   target = "receiverAvatarUrl")
    RelationshipResponse toResponse(Relationship relationship);

    List<RelationshipResponse> toResponseList(List<Relationship> relationships);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "requester", ignore = true)
    @Mapping(target = "receiver", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "acceptedAt", ignore = true)
    void updateEntity(RelationshipRequest request, @MappingTarget Relationship relationship);
}
