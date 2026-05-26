package moderationservice.service;

import moderationservice.contracts.ModerationRuleRequest;
import moderationservice.contracts.ModerationRuleResponse;
import moderationservice.contracts.ModerationRuleStatusRequest;

import java.util.List;

public interface ModerationRuleService {

    List<ModerationRuleResponse> getRules();

    ModerationRuleResponse createRule(ModerationRuleRequest request);

    ModerationRuleResponse updateRule(String id, ModerationRuleRequest request);

    ModerationRuleResponse updateRuleStatus(String id, ModerationRuleStatusRequest request);
}
