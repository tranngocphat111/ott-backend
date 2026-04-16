package mediaservice.mappers;

import mediaservice.dtos.requests.PostRequest;
import mediaservice.dtos.responses.MediaResponse;
import mediaservice.dtos.responses.PostResponse;
import mediaservice.models.ImageMedia;
import mediaservice.models.Media;
import mediaservice.models.Post;
import mediaservice.models.VideoMedia;
import mediaservice.models.enums.MediaType;
import mediaservice.utils.MediaUrlBuilder;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class PostMapper {

    @Autowired
    protected MediaUrlBuilder mediaUrlBuilder;

    @Autowired
    protected ContentAccessControlMapper contentAccessControlMapper;

    @Mapping(target = "hashTags", ignore = true)
    @Mapping(target = "medias",   ignore = true)
    public abstract Post toEntity(PostRequest request);

    @Mapping(target = "hashTags", ignore = true)
    @Mapping(target = "accountId",          source = "account.id")
    @Mapping(target = "accountUsername",     source = "account.username")
    @Mapping(target = "accountDisplayName",  source = "account.displayName")
    @Mapping(target = "accountAvatarUrl",    source = "account.avatarUrl")
    @Mapping(target = "medias",              source = "medias")
    @Mapping(target = "totalReactions",      ignore = true)
    @Mapping(target = "totalComments",       ignore = true)
    @Mapping(target = "totalShares",         ignore = true)
    public abstract PostResponse toResponse(Post post);

    @AfterMapping
    protected void mapAccessControls(Post post, @MappingTarget PostResponse response) {
        if (post.getAccessControls() == null || post.getAccessControls().isEmpty()) {
            response.setAccessControls(new ArrayList<>());
            return;
        }
        response.setAccessControls(
                contentAccessControlMapper.toResponseList(new ArrayList<>(post.getAccessControls())));
    }

    public abstract List<PostResponse> toResponseList(List<Post> posts);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "hashTags", ignore = true)
    @Mapping(target = "medias",   ignore = true)
    public abstract void updateEntity(PostRequest request, @MappingTarget Post post);

    public MediaResponse mediaToResponse(Media media) {
        if (media == null) return null;
        MediaResponse r = new MediaResponse();
        r.setId(media.getId());
        // Convert relative S3 key (e.g. "social/posts/uuid.jpg") to full HTTPS URL
        String S3Url = media instanceof VideoMedia ? "social/videos" : "social/posts";
        r.setUrl(mediaUrlBuilder != null ? mediaUrlBuilder.buildS3Url(S3Url, media.getUrl()) : media.getUrl());
        r.setCaption(media.getCaption());
        r.setOrderIndex(media.getOrderIndex());
        r.setCreatedAt(media.getCreatedAt());
        r.setUpdatedAt(media.getUpdatedAt());
        if (media instanceof VideoMedia vm) {
            r.setType(MediaType.VIDEO_MEDIA);
            // Also resolve thumbnail URL if it's a relative key
            String thumbUrl = vm.getThumbnailUrl();
            r.setThumbnailUrl(thumbUrl != null && mediaUrlBuilder != null
                    ? mediaUrlBuilder.buildS3Url("social/videos", thumbUrl) : thumbUrl);
            r.setDuration(vm.getDuration());
            r.setHasAudio(vm.isHasAudio());
        } else {
            r.setType(MediaType.IMAGE_MEDIA);
        }
        return r;
    }
}
