package moderationservice.controller;

import lombok.RequiredArgsConstructor;
import moderationservice.contracts.ModerationRuleRequest;
import moderationservice.contracts.ModerationRuleResponse;
import moderationservice.contracts.ModerationRuleStatusRequest;
import moderationservice.service.ModerationRuleService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/moderation/rules")
@RequiredArgsConstructor
public class ModerationRuleController {

    private final ModerationRuleService moderationRuleService;

    @GetMapping
    public List<ModerationRuleResponse> getRules() {
        return moderationRuleService.getRules();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ModerationRuleResponse createRule(
            @RequestBody ModerationRuleRequest request,
            @RequestHeader(value = "X-Admin-Id", required = false) String actorId) {
        return moderationRuleService.createRule(request, actorId);
    }

    @PutMapping("/{id}")
    public ModerationRuleResponse updateRule(
            @PathVariable String id,
            @RequestBody ModerationRuleRequest request,
            @RequestHeader(value = "X-Admin-Id", required = false) String actorId) {
        return moderationRuleService.updateRule(id, request, actorId);
    }

    @PatchMapping("/{id}/enabled")
    public ModerationRuleResponse updateRuleStatus(
            @PathVariable String id,
            @RequestBody ModerationRuleStatusRequest request,
            @RequestHeader(value = "X-Admin-Id", required = false) String actorId) {
        return moderationRuleService.updateRuleStatus(id, request, actorId);
    }
}
