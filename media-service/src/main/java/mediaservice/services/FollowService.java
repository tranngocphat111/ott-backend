package mediaservice.services;

import mediaservice.dtos.requests.FollowRequest;
import mediaservice.dtos.responses.FollowResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FollowService {
    FollowResponse createFollow(FollowRequest request);
    FollowResponse getFollowById(String id);
    List<FollowResponse> getAllFollows();
    Page<FollowResponse> getAllFollows(Pageable pageable);
    void deleteFollow(String id);
    List<FollowResponse> getFollowersByTargetId(String targetId);
    List<FollowResponse> getFollowingsByUserId(String userId);
}

