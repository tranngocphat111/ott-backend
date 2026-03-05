package mediaservice.mappers;

import mediaservice.dtos.requests.UserAccountRequest;
import mediaservice.dtos.responses.UserAccountResponse;
import mediaservice.models.UserAccount;
import mediaservice.utils.MediaUrlBuilder;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class UserAccountMapper {

    @Autowired
    protected MediaUrlBuilder mediaUrlBuilder;

    // Explicit mappings for profile fields to assist MapStruct with inheritance chain
    @Mapping(target = "work", source = "work")
    @Mapping(target = "location", source = "location")
    @Mapping(target = "relationshipStatus", source = "relationshipStatus")
    public abstract UserAccount toEntity(UserAccountRequest request);

    // Explicit mappings for response to include profile fields from UserAccount
    @Mapping(target = "work", source = "work")
    @Mapping(target = "location", source = "location")
    @Mapping(target = "relationshipStatus", source = "relationshipStatus")
    public abstract UserAccountResponse toResponse(UserAccount userAccount);

    public abstract List<UserAccountResponse> toResponseList(List<UserAccount> userAccounts);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "work", source = "work")
    @Mapping(target = "location", source = "location")
    @Mapping(target = "relationshipStatus", source = "relationshipStatus")
    public abstract void updateEntity(UserAccountRequest request, @MappingTarget UserAccount userAccount);

    @AfterMapping
    protected void buildFullUrls(@MappingTarget UserAccountResponse response, UserAccount source) {
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
