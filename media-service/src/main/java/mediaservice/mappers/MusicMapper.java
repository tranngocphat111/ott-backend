package mediaservice.mappers;

import mediaservice.dtos.requests.MusicRequest;
import mediaservice.dtos.responses.MusicResponse;
import mediaservice.models.Music;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MusicMapper {

    Music toEntity(MusicRequest request);

    MusicResponse toResponse(Music music);

    List<MusicResponse> toResponseList(List<Music> musics);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(MusicRequest request, @MappingTarget Music music);
}
