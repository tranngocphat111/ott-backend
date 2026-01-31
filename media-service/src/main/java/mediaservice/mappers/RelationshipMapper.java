package mediaservice.mappers;

import mediaservice.dtos.requests.RelationshipRequest;
import mediaservice.dtos.responses.RelationshipResponse;
import mediaservice.models.Relationship;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RelationshipMapper {

    Relationship toEntity(RelationshipRequest request);

    RelationshipResponse toResponse(Relationship relationship);

    List<RelationshipResponse> toResponseList(List<Relationship> relationships);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(RelationshipRequest request, @MappingTarget Relationship relationship);
}
