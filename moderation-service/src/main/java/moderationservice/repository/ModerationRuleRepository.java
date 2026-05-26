package moderationservice.repository;

import moderationservice.entity.ModerationRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModerationRuleRepository extends JpaRepository<ModerationRule, String> {

    List<ModerationRule> findByEnabledTrue();

    List<ModerationRule> findAllByOrderByCreatedAtDesc();

    boolean existsByNormalizedTerm(String normalizedTerm);

    boolean existsByNormalizedTermAndIdNot(String normalizedTerm, String id);
}
