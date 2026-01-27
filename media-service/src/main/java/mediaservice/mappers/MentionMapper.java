package mediaservice.mappers;

import mediaservice.dtos.requests.MentionRequest;
import mediaservice.dtos.responses.MentionResponse;
import mediaservice.models.Mention;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MentionMapper {

    Mention toEntity(MentionRequest request);

    MentionResponse toResponse(Mention mention);

    List<MentionResponse> toResponseList(List<Mention> mentions);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(MentionRequest request, @MappingTarget Mention mention);
}
