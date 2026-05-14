package mediaservice.mappers;

import mediaservice.dtos.requests.AccessControlRequest;
import mediaservice.dtos.responses.ContentAccessControlResponse;
import mediaservice.models.ContentAccessControl;
import org.mapstruct.*;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ContentAccessControlMapper {

    ContentAccessControl toEntity(AccessControlRequest request);

    @Mapping(source = "account.id", target = "accountId")
    @Mapping(source = "account.username", target = "accountUsername")
    @Mapping(source = "content.id", target = "contentId")
    ContentAccessControlResponse toResponse(ContentAccessControl contentAccessControl);

    List<ContentAccessControlResponse> toResponseList(List<ContentAccessControl> contentAccessControls);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(AccessControlRequest request, @MappingTarget ContentAccessControl contentAccessControl);
}
