package mediaservice.mappers;

import mediaservice.dtos.requests.ReactionRequest;
import mediaservice.dtos.responses.ReactionResponse;
import mediaservice.models.Reaction;
import org.mapstruct.*;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReactionMapper {

    @Mapping(target = "accountId",      source = "account.id")
    @Mapping(target = "accountUsername", source = "account.username")
    @Mapping(target = "accountDisplayName", source = "account.displayName")
    @Mapping(target = "accountAvatarUrl", source = "account.avatarUrl")
    @Mapping(target = "createdAt", source = "createdAt", dateFormat = "yyyy-MM-dd'T'HH:mm:ss")
    ReactionResponse toResponse(Reaction reaction);

    List<ReactionResponse> toResponseList(List<Reaction> reactions);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(ReactionRequest request, @MappingTarget Reaction reaction);
}
