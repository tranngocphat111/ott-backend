package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.CreatorProfileRequest;
import mediaservice.dtos.responses.CreatorProfileResponse;
import mediaservice.mappers.CreatorProfileMapper;
import mediaservice.models.CreatorProfile;
import mediaservice.repositories.CreatorProfileRepository;
import mediaservice.services.CreatorProfileService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CreatorProfileServiceImpl implements CreatorProfileService {

    private final CreatorProfileRepository creatorProfileRepository;
    private final CreatorProfileMapper creatorProfileMapper;

    @Override
    @Transactional
    public CreatorProfileResponse createCreatorProfile(CreatorProfileRequest request) {
        CreatorProfile creatorProfile = creatorProfileMapper.toEntity(request);
        CreatorProfile savedCreatorProfile = creatorProfileRepository.save(creatorProfile);
        return creatorProfileMapper.toResponse(savedCreatorProfile);
    }

    @Override
    @Transactional(readOnly = true)
    public CreatorProfileResponse getCreatorProfileById(String id) {
        CreatorProfile creatorProfile = creatorProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Creator profile not found with id: " + id));
        return creatorProfileMapper.toResponse(creatorProfile);
    }

    @Override
    @Transactional(readOnly = true)
    public CreatorProfileResponse getCreatorProfileByUserId(String userId) {
        CreatorProfile creatorProfile = creatorProfileRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Creator profile not found for user id: " + userId));
        return creatorProfileMapper.toResponse(creatorProfile);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreatorProfileResponse> getAllCreatorProfiles() {
        List<CreatorProfile> creatorProfiles = creatorProfileRepository.findAll();
        return creatorProfileMapper.toResponseList(creatorProfiles);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CreatorProfileResponse> getAllCreatorProfiles(Pageable pageable) {
        Page<CreatorProfile> creatorProfiles = creatorProfileRepository.findAll(pageable);
        return creatorProfiles.map(creatorProfileMapper::toResponse);
    }

    @Override
    @Transactional
    public CreatorProfileResponse updateCreatorProfile(String id, CreatorProfileRequest request) {
        CreatorProfile creatorProfile = creatorProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Creator profile not found with id: " + id));
        creatorProfileMapper.updateEntity(request, creatorProfile);
        CreatorProfile updatedCreatorProfile = creatorProfileRepository.save(creatorProfile);
        return creatorProfileMapper.toResponse(updatedCreatorProfile);
    }

    @Override
    @Transactional
    public void deleteCreatorProfile(String id) {
        if (!creatorProfileRepository.existsById(id)) {
            throw new RuntimeException("Creator profile not found with id: " + id);
        }
        creatorProfileRepository.deleteById(id);
    }
}

