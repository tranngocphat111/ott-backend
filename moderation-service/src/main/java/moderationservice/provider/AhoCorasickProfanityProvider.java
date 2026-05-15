package moderationservice.provider;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import moderationservice.entity.ModerationRule;
import moderationservice.repository.ModerationRuleRepository;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AhoCorasickProfanityProvider {

    private final ModerationRuleRepository moderationRuleRepository;

    private Trie trie;

    @PostConstruct
    public void loadDictionary() {
        List<ModerationRule> rules = moderationRuleRepository.findByEnabledTrue();
        Trie.TrieBuilder builder = Trie.builder()
                .ignoreOverlaps()
                .ignoreCase()
                .onlyWholeWords();

        int loadedTerms = 0;
        for (ModerationRule rule : rules) {
            String normalizedTerm = rule.getNormalizedTerm();
            if (normalizedTerm == null || normalizedTerm.isBlank()) {
                log.warn("Skipping moderation rule with blank normalizedTerm: ruleId={}", rule.getId());
                continue;
            }
            builder.addKeyword(normalizedTerm.trim());
            loadedTerms++;
        }

        trie = builder.build();
        log.info("Loaded profanity dictionary: enabledRules={}, loadedTerms={}", rules.size(), loadedTerms);
    }

    public List<String> scanText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (trie == null) {
            log.warn("Profanity trie is not initialized; returning no matches");
            return List.of();
        }

        Collection<Emit> emits = trie.parseText(text);
        return emits.stream()
                .map(Emit::getKeyword)
                .distinct()
                .toList();
    }
}
