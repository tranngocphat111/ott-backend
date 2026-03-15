package mediaservice.services;

import mediaservice.dtos.requests.ReactionRequest;
import mediaservice.dtos.responses.ReactionResponse;
import mediaservice.models.enums.ReactionTargetType;
import mediaservice.models.enums.ReactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ReactionService {
    ReactionResponse createReaction(ReactionRequest request);
    ReactionResponse getReactionById(String id);
    List<ReactionResponse> getAllReactions();
    Page<ReactionResponse> getAllReactions(Pageable pageable);
    void deleteReaction(String id);
    List<ReactionResponse> getReactionsByTargetId(String targetId);
    List<ReactionResponse> getReactionsByAccountId(String accountId);
    /** Toggle like – trả về null nếu unliked, ReactionResponse nếu liked */
    ReactionResponse toggleReaction(String accountId, String targetId,
                                    ReactionTargetType targetType, ReactionType reactionType);
}

