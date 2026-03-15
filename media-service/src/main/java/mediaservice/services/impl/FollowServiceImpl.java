package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.FollowRequest;
import mediaservice.dtos.responses.FollowResponse;
import mediaservice.mappers.FollowMapper;
import mediaservice.models.Follow;
import mediaservice.repositories.FollowRepository;
import mediaservice.services.FollowService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final FollowRepository followRepository;
    private final FollowMapper followMapper;

    @Override
    @Transactional
    public FollowResponse createFollow(FollowRequest request) {
        Follow follow = followMapper.toEntity(request);
        Follow savedFollow = followRepository.save(follow);
        return followMapper.toResponse(savedFollow);
    }

    @Override
    @Transactional(readOnly = true)
    public FollowResponse getFollowById(String id) {
        Follow follow = followRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Follow not found with id: " + id));
        return followMapper.toResponse(follow);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowResponse> getAllFollows() {
        List<Follow> follows = followRepository.findAll();
        return followMapper.toResponseList(follows);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FollowResponse> getAllFollows(Pageable pageable) {
        Page<Follow> follows = followRepository.findAll(pageable);
        return follows.map(followMapper::toResponse);
    }

    @Override
    @Transactional
    public void deleteFollow(String id) {
        if (!followRepository.existsById(id)) {
            throw new RuntimeException("Follow not found with id: " + id);
        }
        followRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowResponse> getFollowersByTargetId(String targetId) {
        List<Follow> follows = followRepository.findAll(); // TODO: Add custom query
        return followMapper.toResponseList(follows);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowResponse> getFollowingsByUserId(String userId) {
        List<Follow> follows = followRepository.findAll(); // TODO: Add custom query
        return followMapper.toResponseList(follows);
    }
}

