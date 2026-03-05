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
    @CacheEvict(value = {"posts", "allPosts"}, allEntries = true)
    public void deleteReaction(String id) {
        if (!reactionRepository.existsById(id)) {
            throw new RuntimeException("Reaction not found with id: " + id);
        }
        reactionRepository.deleteById(id);
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
        Optional<Reaction> existing = reactionRepository
                .findByAccountAndTargetIdAndTargetType(account, targetId, targetType);
        if (existing.isPresent()) {
            reactionRepository.delete(existing.get());
            return null; // unliked
        }
        Reaction reaction = new Reaction();
        reaction.setAccount(account);
        reaction.setTargetId(targetId);
        reaction.setTargetType(targetType);
        reaction.setReactionType(reactionType != null ? reactionType : ReactionType.LIKE);
        return reactionMapper.toResponse(reactionRepository.save(reaction));
    }
}

