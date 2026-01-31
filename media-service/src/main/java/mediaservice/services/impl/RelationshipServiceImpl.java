package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.RelationshipRequest;
import mediaservice.dtos.responses.RelationshipResponse;
import mediaservice.mappers.RelationshipMapper;
import mediaservice.models.Relationship;
import mediaservice.repositories.RelationshipRepository;
import mediaservice.services.RelationshipService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RelationshipServiceImpl implements RelationshipService {

    private final RelationshipRepository relationshipRepository;
    private final RelationshipMapper relationshipMapper;

    @Override
    @Transactional
    public RelationshipResponse createRelationship(RelationshipRequest request) {
        Relationship relationship = relationshipMapper.toEntity(request);
        Relationship savedRelationship = relationshipRepository.save(relationship);
        return relationshipMapper.toResponse(savedRelationship);
    }

    @Override
    @Transactional(readOnly = true)
    public RelationshipResponse getRelationshipById(String id) {
        Relationship relationship = relationshipRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Relationship not found with id: " + id));
        return relationshipMapper.toResponse(relationship);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelationshipResponse> getAllRelationships() {
        List<Relationship> relationships = relationshipRepository.findAll();
        return relationshipMapper.toResponseList(relationships);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RelationshipResponse> getAllRelationships(Pageable pageable) {
        Page<Relationship> relationships = relationshipRepository.findAll(pageable);
        return relationships.map(relationshipMapper::toResponse);
    }

    @Override
    @Transactional
    public RelationshipResponse updateRelationship(String id, RelationshipRequest request) {
        Relationship relationship = relationshipRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Relationship not found with id: " + id));
        relationshipMapper.updateEntity(request, relationship);
        Relationship updatedRelationship = relationshipRepository.save(relationship);
        return relationshipMapper.toResponse(updatedRelationship);
    }

    @Override
    @Transactional
    public void deleteRelationship(String id) {
        if (!relationshipRepository.existsById(id)) {
            throw new RuntimeException("Relationship not found with id: " + id);
        }
        relationshipRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelationshipResponse> getRelationshipsByUserId(String userId) {
        List<Relationship> relationships = relationshipRepository.findAll(); // TODO: Add custom query
        return relationshipMapper.toResponseList(relationships);
    }
}

