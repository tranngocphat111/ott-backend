package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.HashTagRequest;
import mediaservice.dtos.responses.HashTagResponse;
import mediaservice.mappers.HashTagMapper;
import mediaservice.models.HashTag;
import mediaservice.repositories.HashTagRepository;
import mediaservice.services.HashTagService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HashTagServiceImpl implements HashTagService {

    private final HashTagRepository hashTagRepository;
    private final HashTagMapper hashTagMapper;

    @Override
    @Transactional
    public HashTagResponse createHashTag(HashTagRequest request) {
        HashTag hashTag = hashTagMapper.toEntity(request);
        HashTag savedHashTag = hashTagRepository.save(hashTag);
        return hashTagMapper.toResponse(savedHashTag);
    }

    @Override
    @Transactional(readOnly = true)
    public HashTagResponse getHashTagById(String id) {
        HashTag hashTag = hashTagRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("HashTag not found with id: " + id));
        return hashTagMapper.toResponse(hashTag);
    }

    @Override
    @Transactional(readOnly = true)
    public HashTagResponse getHashTagByName(String name) {
        HashTag hashTag = hashTagRepository.findAll().stream()
                .filter(h -> h.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("HashTag not found with name: " + name));
        return hashTagMapper.toResponse(hashTag);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HashTagResponse> getAllHashTags() {
        List<HashTag> hashTags = hashTagRepository.findAll();
        return hashTagMapper.toResponseList(hashTags);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<HashTagResponse> getAllHashTags(Pageable pageable) {
        Page<HashTag> hashTags = hashTagRepository.findAll(pageable);
        return hashTags.map(hashTagMapper::toResponse);
    }

    @Override
    @Transactional
    public HashTagResponse updateHashTag(String id, HashTagRequest request) {
        HashTag hashTag = hashTagRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("HashTag not found with id: " + id));
        hashTagMapper.updateEntity(request, hashTag);
        HashTag updatedHashTag = hashTagRepository.save(hashTag);
        return hashTagMapper.toResponse(updatedHashTag);
    }

    @Override
    @Transactional
    public void deleteHashTag(String id) {
        if (!hashTagRepository.existsById(id)) {
            throw new RuntimeException("HashTag not found with id: " + id);
        }
        hashTagRepository.deleteById(id);
    }
}

