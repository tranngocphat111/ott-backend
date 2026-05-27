package moderationservice.service;

import lombok.RequiredArgsConstructor;
import moderationservice.contracts.ModerationRuleRequest;
import moderationservice.contracts.ModerationRuleResponse;
import moderationservice.contracts.ModerationRuleStatusRequest;
import moderationservice.entity.ModerationRule;
import moderationservice.enums.ViolationSeverity;
import moderationservice.provider.AhoCorasickProfanityProvider;
import moderationservice.repository.ModerationRuleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ModerationRuleServiceImpl implements ModerationRuleService {

    private final ModerationRuleRepository moderationRuleRepository;
    private final AhoCorasickProfanityProvider profanityProvider;

    @Override
    @Transactional(readOnly = true)
    public List<ModerationRuleResponse> getRules() {
        return moderationRuleRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ModerationRuleResponse createRule(ModerationRuleRequest request) {
        validateRequest(request);
        String normalizedTerm = normalizeTerm(request.getTerm());
        if (moderationRuleRepository.existsByNormalizedTerm(normalizedTerm)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Moderation rule already exists");
        }

        ModerationRule rule = ModerationRule.builder()
                .term(request.getTerm().trim())
                .normalizedTerm(normalizedTerm)
                .category(request.getCategory().trim())
                .language(request.getLanguage().trim().toLowerCase(Locale.ROOT))
                .severity(request.getSeverity())
                .enabled(request.getEnabled() == null || request.getEnabled())
                .build();

        ModerationRule saved = moderationRuleRepository.save(rule);
        reloadDictionaryAfterCommit();
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ModerationRuleResponse updateRule(String id, ModerationRuleRequest request) {
        validateId(id);
        validateRequest(request);

        ModerationRule rule = getRuleOrThrow(id);
        String normalizedTerm = normalizeTerm(request.getTerm());
        if (moderationRuleRepository.existsByNormalizedTermAndIdNot(normalizedTerm, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Moderation rule already exists");
        }

        rule.setTerm(request.getTerm().trim());
        rule.setNormalizedTerm(normalizedTerm);
        rule.setCategory(request.getCategory().trim());
        rule.setLanguage(request.getLanguage().trim().toLowerCase(Locale.ROOT));
        rule.setSeverity(request.getSeverity());
        rule.setEnabled(request.getEnabled() == null || request.getEnabled());

        ModerationRule saved = moderationRuleRepository.save(rule);
        reloadDictionaryAfterCommit();
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ModerationRuleResponse updateRuleStatus(String id, ModerationRuleStatusRequest request) {
        validateId(id);
        if (request == null || request.getEnabled() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enabled is required");
        }

        ModerationRule rule = getRuleOrThrow(id);
        rule.setEnabled(request.getEnabled());

        ModerationRule saved = moderationRuleRepository.save(rule);
        reloadDictionaryAfterCommit();
        return toResponse(saved);
    }

    private ModerationRule getRuleOrThrow(String id) {
        return moderationRuleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Moderation rule not found"));
    }

    private void validateRequest(ModerationRuleRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        requireText(request.getTerm(), "term");
        requireText(request.getCategory(), "category");
        requireText(request.getLanguage(), "language");
        requireSeverity(request.getSeverity());
    }

    private void validateId(String id) {
        requireText(id, "id");
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
    }

    private void requireSeverity(ViolationSeverity severity) {
        if (severity == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "severity is required");
        }
    }

    private String normalizeTerm(String term) {
        String normalized = Normalizer.normalize(term.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return normalized.toLowerCase(Locale.ROOT);
    }

    private void reloadDictionaryAfterCommit() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            profanityProvider.reloadDictionary();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                profanityProvider.reloadDictionary();
            }
        });
    }

    private ModerationRuleResponse toResponse(ModerationRule rule) {
        return ModerationRuleResponse.builder()
                .id(rule.getId())
                .term(rule.getTerm())
                .normalizedTerm(rule.getNormalizedTerm())
                .category(rule.getCategory())
                .language(rule.getLanguage())
                .severity(rule.getSeverity())
                .enabled(rule.getEnabled())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
