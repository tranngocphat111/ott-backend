package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.ReactionRequest;
import mediaservice.dtos.responses.ReactionResponse;
import mediaservice.mappers.ReactionMapper;
import mediaservice.models.Account;
import mediaservice.models.Reaction;
import mediaservice.models.enums.ReactionTargetType;
import mediaservice.models.enums.ReactionType;
import mediaservice.repositories.AccountRepository;
import mediaservice.repositories.ReactionRepository;
import mediaservice.services.ReactionService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReactionServiceImpl implements ReactionService {

    private final ReactionRepository reactionRepository;
    private final ReactionMapper reactionMapper;
    private final AccountRepository accountRepository;
    private final mediaservice.realtime.PostActivityPublisher postActivityPublisher;

    @Override
    @Transactional
    @CacheEvict(value = {"posts", "allPosts"}, allEntries = true)
    public ReactionResponse createReaction(ReactionRequest request) {
        Account account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new RuntimeException("Account not found: " + request.getAccountId()));
        Reaction reaction = new Reaction();
        reaction.setAccount(account);
        reaction.setTargetId(request.getTargetId());
        reaction.setTargetType(request.getTargetType());
        reaction.setReactionType(request.getReactionType() != null ? request.getReactionType() : ReactionType.LIKE);
        Reaction savedReaction = reactionRepository.save(reaction);
        ReactionResponse response = reactionMapper.toResponse(savedReaction);
        if (ReactionTargetType.POST.equals(savedReaction.getTargetType())) {
            postActivityPublisher.publish(savedReaction.getTargetId(), "REACTION", "CREATE", response);
        }
        return response;
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
    @CacheEvict(value = {"posts", "allPosts"}, allEntries = true)
    public void deleteReaction(String id) {
        Reaction reaction = reactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reaction not found with id: " + id));
        reactionRepository.deleteById(id);
        if (ReactionTargetType.POST.equals(reaction.getTargetType())) {
            postActivityPublisher.publish(reaction.getTargetId(), "REACTION", "DELETE", reactionMapper.toResponse(reaction));
        }
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "reactions", key = "#targetId + ':' + #targetType", unless = "#result == null || #result.isEmpty()")
    public List<ReactionResponse> getReactionsByTargetId(String targetId) {
        List<Reaction> reactions = reactionRepository.findByTargetIdAndTargetType(
                targetId, ReactionTargetType.POST);
        return reactionMapper.toResponseList(reactions);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReactionResponse> getReactionsByAccountId(String accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));
        List<Reaction> reactions = reactionRepository.findByAccount(account);
        return reactionMapper.toResponseList(reactions);
    }

    /**
     * Toggle like/react trên một POST.
     * - Nếu đã react → xoá và trả về null (unliked)
     * - Nếu chưa react → tạo mới và trả về ReactionResponse
     */
    @Override
    @Transactional
    @CacheEvict(value = {"posts", "allPosts", "reactions"}, allEntries = true)
    public ReactionResponse toggleReaction(String accountId, String targetId,
                                           ReactionTargetType targetType, ReactionType reactionType) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));
        List<Reaction> existing = reactionRepository
                .findByAccountAndTargetIdAndTargetType(account, targetId, targetType);
        
        ReactionType targetReactionType = reactionType != null ? reactionType : ReactionType.LIKE;

        if (!existing.isEmpty()) {
            Reaction firstExisting = existing.get(0);
            
            if (firstExisting.getReactionType() == targetReactionType) {
                // Same reaction -> Unlike
                for (Reaction oldReaction : existing) {
                    ReactionResponse response = reactionMapper.toResponse(oldReaction);
                    if (ReactionTargetType.POST.equals(targetType)) {
                        postActivityPublisher.publish(targetId, "REACTION", "DELETE", response);
                    }
                }
                reactionRepository.deleteAll(existing);
                return null;
            } else {
                // Different reaction -> Switch
                for (Reaction oldReaction : existing) {
                    ReactionResponse response = reactionMapper.toResponse(oldReaction);
                    if (ReactionTargetType.POST.equals(targetType)) {
                        postActivityPublisher.publish(targetId, "REACTION", "DELETE", response);
                    }
                }
                reactionRepository.deleteAll(existing);
                
                Reaction newReaction = new Reaction();
                newReaction.setAccount(account);
                newReaction.setTargetId(targetId);
                newReaction.setTargetType(targetType);
                newReaction.setReactionType(targetReactionType);
                Reaction savedReaction = reactionRepository.save(newReaction);
                ReactionResponse response = reactionMapper.toResponse(savedReaction);
                if (ReactionTargetType.POST.equals(targetType)) {
                    postActivityPublisher.publish(targetId, "REACTION", "CREATE", response);
                }
                return response;
            }
        }
        
        Reaction reaction = new Reaction();
        reaction.setAccount(account);
        reaction.setTargetId(targetId);
        reaction.setTargetType(targetType);
        reaction.setReactionType(targetReactionType);
        Reaction savedReaction = reactionRepository.save(reaction);
        ReactionResponse response = reactionMapper.toResponse(savedReaction);
        if (ReactionTargetType.POST.equals(targetType)) {
            postActivityPublisher.publish(targetId, "REACTION", "CREATE", response);
        }
        return response;
    }
}

