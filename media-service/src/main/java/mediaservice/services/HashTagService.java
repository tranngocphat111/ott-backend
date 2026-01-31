package mediaservice.services;

import org.springframework.data.domain.Pageable;
import mediaservice.dtos.requests.HashTagRequest;
import mediaservice.dtos.responses.HashTagResponse;
import org.springframework.data.domain.Page;
import java.util.List;

public interface HashTagService {
    HashTagResponse createHashTag(HashTagRequest request);
    HashTagResponse getHashTagById(String id);
    HashTagResponse getHashTagByName(String name);
    List<HashTagResponse> getAllHashTags();
    Page<HashTagResponse> getAllHashTags(Pageable pageable);
    HashTagResponse updateHashTag(String id, HashTagRequest request);
    void deleteHashTag(String id);
}


