package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.ReactionRequest;
import mediaservice.dtos.responses.ReactionResponse;
import mediaservice.mappers.ReactionMapper;
import mediaservice.models.Reaction;
import mediaservice.repositories.ReactionRepository;
import mediaservice.services.ReactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReactionServiceImpl implements ReactionService {

    private final ReactionRepository reactionRepository;
    private final ReactionMapper reactionMapper;

    @Override
    @Transactional
    public ReactionResponse createReaction(ReactionRequest request) {
        Reaction reaction = reactionMapper.toEntity(request);
        Reaction savedReaction = reactionRepository.save(reaction);
        return reactionMapper.toResponse(savedReaction);
    }

    @Override
    @Transactional(readOnly = true)
    public ReactionResponse getReactionById(String id) {
        Reaction reaction = reactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reaction not found with id: " + id));
        return reactionMapper.toResponse(reaction);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReactionResponse> getAllReactions() {
        List<Reaction> reactions = reactionRepository.findAll();
        return reactionMapper.toResponseList(reactions);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReactionResponse> getAllReactions(Pageable pageable) {
        Page<Reaction> reactions = reactionRepository.findAll(pageable);
        return reactions.map(reactionMapper::toResponse);
    }

    @Override
    @Transactional
    public void deleteReaction(String id) {
        if (!reactionRepository.existsById(id)) {
            throw new RuntimeException("Reaction not found with id: " + id);
        }
        reactionRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReactionResponse> getReactionsByTargetId(String targetId) {
        List<Reaction> reactions = reactionRepository.findAll(); // TODO: Add custom query
        return reactionMapper.toResponseList(reactions);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReactionResponse> getReactionsByAccountId(String accountId) {
        List<Reaction> reactions = reactionRepository.findAll(); // TODO: Add custom query
        return reactionMapper.toResponseList(reactions);
    }
}

