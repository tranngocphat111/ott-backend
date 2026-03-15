package mediaservice.services;

import mediaservice.dtos.requests.CreatorProfileRequest;
import mediaservice.dtos.responses.CreatorProfileResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CreatorProfileService {
    CreatorProfileResponse createCreatorProfile(CreatorProfileRequest request);
    CreatorProfileResponse getCreatorProfileById(String id);
    CreatorProfileResponse getCreatorProfileByUserId(String userId);
    List<CreatorProfileResponse> getAllCreatorProfiles();
    Page<CreatorProfileResponse> getAllCreatorProfiles(Pageable pageable);
    CreatorProfileResponse updateCreatorProfile(String id, CreatorProfileRequest request);
    void deleteCreatorProfile(String id);
}

