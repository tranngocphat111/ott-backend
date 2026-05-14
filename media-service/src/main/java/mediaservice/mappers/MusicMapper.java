package mediaservice.mappers;

import mediaservice.dtos.requests.MusicRequest;
import mediaservice.dtos.responses.MusicResponse;
import mediaservice.models.Music;
import mediaservice.utils.MediaUrlBuilder;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class MusicMapper {

    @Autowired
    protected MediaUrlBuilder mediaUrlBuilder;

    public abstract Music toEntity(MusicRequest request);

    public abstract MusicResponse toResponse(Music music);

    public abstract List<MusicResponse> toResponseList(List<Music> musics);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    public abstract void updateEntity(MusicRequest request, @MappingTarget Music music);

    /**
     * Post-process response to build full URL
     */
    @AfterMapping
    protected void buildFullUrls(@MappingTarget MusicResponse response, Music source) {
        if (source.getAudioUrl() != null) {
            response.setAudioUrl(convertToFullUrl(source.getAudioUrl()));
        }
    }

    private String convertToFullUrl(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }
        if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
            return relativePath;
        }
        return mediaUrlBuilder.buildS3Url("", relativePath);
    }
}
