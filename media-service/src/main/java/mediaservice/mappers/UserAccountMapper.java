package mediaservice.mappers;

import mediaservice.dtos.requests.UserAccountRequest;
import mediaservice.dtos.responses.UserAccountResponse;
import mediaservice.models.UserAccount;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserAccountMapper {

    UserAccount toEntity(UserAccountRequest request);

    UserAccountResponse toResponse(UserAccount userAccount);

    List<UserAccountResponse> toResponseList(List<UserAccount> userAccounts);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UserAccountRequest request, @MappingTarget UserAccount userAccount);
}
