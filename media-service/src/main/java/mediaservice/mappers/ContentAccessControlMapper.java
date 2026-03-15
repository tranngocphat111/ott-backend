package mediaservice.mappers;

import mediaservice.dtos.requests.AccessControlRequest;
import mediaservice.dtos.responses.ContentAccessControlResponse;
import mediaservice.models.ContentAccessControl;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ContentAccessControlMapper {

    ContentAccessControl toEntity(AccessControlRequest request);

    ContentAccessControlResponse toResponse(ContentAccessControl contentAccessControl);

    List<ContentAccessControlResponse> toResponseList(List<ContentAccessControl> contentAccessControls);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(AccessControlRequest request, @MappingTarget ContentAccessControl contentAccessControl);
}
