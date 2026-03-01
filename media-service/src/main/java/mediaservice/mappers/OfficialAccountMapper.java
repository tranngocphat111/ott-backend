package mediaservice.mappers;

import mediaservice.dtos.requests.OfficialAccountRequest;
import mediaservice.dtos.responses.OfficialAccountResponse;
import mediaservice.models.OfficialAccount;
import mediaservice.utils.MediaUrlBuilder;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class OfficialAccountMapper {

    @Autowired
    protected MediaUrlBuilder mediaUrlBuilder;

    public abstract OfficialAccount toEntity(OfficialAccountRequest request);

    public abstract OfficialAccountResponse toResponse(OfficialAccount officialAccount);

    public abstract List<OfficialAccountResponse> toResponseList(List<OfficialAccount> officialAccounts);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    public abstract void updateEntity(OfficialAccountRequest request, @MappingTarget OfficialAccount officialAccount);

    @AfterMapping
    protected void buildFullUrls(@MappingTarget OfficialAccountResponse response, OfficialAccount source) {
        if (source.getAvatarUrl() != null) {
            response.setAvatarUrl(convertToFullUrl(source.getAvatarUrl()));
        }
        if (source.getCoverUrl() != null) {
            response.setCoverUrl(convertToFullUrl(source.getCoverUrl()));
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
