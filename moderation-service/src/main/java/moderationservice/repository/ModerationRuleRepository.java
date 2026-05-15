package moderationservice.repository;

import moderationservice.entity.ModerationRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModerationRuleRepository extends JpaRepository<ModerationRule, String> {

    List<ModerationRule> findByEnabledTrue();
}
