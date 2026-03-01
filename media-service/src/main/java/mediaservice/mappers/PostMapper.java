package mediaservice.mappers;

import mediaservice.dtos.requests.PostRequest;
import mediaservice.dtos.responses.MediaResponse;
import mediaservice.dtos.responses.PostResponse;
import mediaservice.models.Media;
import mediaservice.models.Post;
import mediaservice.models.VideoMedia;
import mediaservice.models.enums.MediaType;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PostMapper {

    @Mapping(target = "hashTags", ignore = true)
    @Mapping(target = "medias",   ignore = true)
    Post toEntity(PostRequest request);

    @Mapping(target = "hashTags", ignore = true)
    @Mapping(target = "accountId",          source = "account.id")
    @Mapping(target = "accountUsername",     source = "account.username")
    @Mapping(target = "accountDisplayName",  source = "account.displayName")
    @Mapping(target = "accountAvatarUrl",    source = "account.avatarUrl")
    @Mapping(target = "medias",              source = "medias")
    @Mapping(target = "totalReactions",      ignore = true)
    @Mapping(target = "totalComments",       ignore = true)
    @Mapping(target = "totalShares",         ignore = true)
    PostResponse toResponse(Post post);

    List<PostResponse> toResponseList(List<Post> posts);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "hashTags", ignore = true)
    @Mapping(target = "medias",   ignore = true)
    void updateEntity(PostRequest request, @MappingTarget Post post);

    default MediaResponse mediaToResponse(Media media) {
        if (media == null) return null;
        MediaResponse r = new MediaResponse();
        r.setId(media.getId());
        r.setUrl(media.getUrl());
        r.setCaption(media.getCaption());
        r.setOrderIndex(media.getOrderIndex());
        r.setCreatedAt(media.getCreatedAt());
        r.setUpdatedAt(media.getUpdatedAt());
        if (media instanceof VideoMedia vm) {
            r.setType(MediaType.VIDEO_MEDIA);
            r.setThumbnailUrl(vm.getThumbnailUrl());
            r.setDuration(vm.getDuration());
            r.setHasAudio(vm.isHasAudio());
        } else {
            r.setType(MediaType.IMAGE_MEDIA);
        }
        return r;
    }
}
