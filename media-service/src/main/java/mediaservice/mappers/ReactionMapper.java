package mediaservice.mappers;

import mediaservice.dtos.requests.ReactionRequest;
import mediaservice.dtos.responses.ReactionResponse;
import mediaservice.models.Reaction;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ReactionMapper {

    @Mapping(target = "accountId",      source = "account.id")
    @Mapping(target = "accountUsername", source = "account.username")
    @Mapping(target = "accountAvatarUrl", source = "account.avatarUrl")
    ReactionResponse toResponse(Reaction reaction);

    List<ReactionResponse> toResponseList(List<Reaction> reactions);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(ReactionRequest request, @MappingTarget Reaction reaction);
}
