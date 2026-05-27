package moderationservice.provider;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import moderationservice.entity.ModerationRule;
import moderationservice.enums.ViolationSeverity;
import moderationservice.repository.ModerationRuleRepository;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
@Slf4j
public class AhoCorasickProfanityProvider {

    private final ModerationRuleRepository moderationRuleRepository;

    private final AtomicReference<Trie> trieRef = new AtomicReference<>();

    @PostConstruct
    public void loadDictionary() {
        seedDefaultRulesIfEmpty();
        reloadDictionary();
    }

    public void reloadDictionary() {
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

        Trie newTrie = builder.build();
        trieRef.set(newTrie);
        log.info("Loaded profanity dictionary: enabledRules={}, loadedTerms={}", rules.size(), loadedTerms);
    }

    public List<String> scanText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Trie currentTrie = trieRef.get();
        if (currentTrie == null) {
            log.warn("Profanity trie is not initialized; returning no matches");
            return List.of();
        }

        Collection<Emit> emits = currentTrie.parseText(normalize(text));
        return emits.stream()
                .map(Emit::getKeyword)
                .distinct()
                .toList();
    }

    private String normalize(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return normalized.toLowerCase(Locale.ROOT);
    }

    private void seedDefaultRulesIfEmpty() {
        if (moderationRuleRepository.count() > 0) {
            return;
        }

        List<ModerationRule> defaults = List.of(
                buildDefaultRule("spam", "spam", "abuse", "en", ViolationSeverity.MEDIUM),
                buildDefaultRule("scam", "scam", "fraud", "en", ViolationSeverity.HIGH),
                buildDefaultRule("phishing", "phishing", "fraud", "en", ViolationSeverity.HIGH),
                buildDefaultRule("malware", "malware", "security", "en", ViolationSeverity.HIGH),
                buildDefaultRule("lua dao", "lua dao", "fraud", "vi", ViolationSeverity.HIGH)
        );

        moderationRuleRepository.saveAll(defaults);
        log.info("Seeded default moderation rules: count={}", defaults.size());
    }

    private ModerationRule buildDefaultRule(
            String term,
            String normalizedTerm,
            String category,
            String language,
            ViolationSeverity severity) {
        return ModerationRule.builder()
                .term(term)
                .normalizedTerm(normalizedTerm)
                .category(category)
                .language(language)
                .severity(severity)
                .enabled(true)
                .build();
    }
}
