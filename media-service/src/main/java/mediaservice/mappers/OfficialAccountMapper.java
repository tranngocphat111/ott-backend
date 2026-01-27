package mediaservice.mappers;

import mediaservice.dtos.requests.OfficialAccountRequest;
import mediaservice.dtos.responses.OfficialAccountResponse;
import mediaservice.models.OfficialAccount;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OfficialAccountMapper {

    OfficialAccount toEntity(OfficialAccountRequest request);

    OfficialAccountResponse toResponse(OfficialAccount officialAccount);

    List<OfficialAccountResponse> toResponseList(List<OfficialAccount> officialAccounts);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(OfficialAccountRequest request, @MappingTarget OfficialAccount officialAccount);
}
