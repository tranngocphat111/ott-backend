package mediaservice.services;

import mediaservice.dtos.requests.RelationshipRequest;
import mediaservice.dtos.responses.RelationshipResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface RelationshipService {
    RelationshipResponse createRelationship(RelationshipRequest request);
    RelationshipResponse getRelationshipById(String id);
    List<RelationshipResponse> getAllRelationships();
    Page<RelationshipResponse> getAllRelationships(Pageable pageable);
    RelationshipResponse updateRelationship(String id, RelationshipRequest request);
    void deleteRelationship(String id);
    List<RelationshipResponse> getRelationshipsByUserId(String userId);
}

