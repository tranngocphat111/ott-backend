package mediaservice.mappers;

import mediaservice.dtos.requests.NoteRequest;
import mediaservice.dtos.responses.NoteResponse;
import mediaservice.models.Note;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface NoteMapper {

    @Mapping(target = "hashTags", ignore = true)
    Note toEntity(NoteRequest request);

    @Mapping(target = "hashTags", ignore = true)
    NoteResponse toResponse(Note note);

    List<NoteResponse> toResponseList(List<Note> notes);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "hashTags", ignore = true)
    void updateEntity(NoteRequest request, @MappingTarget Note note);
}
