package mediaservice.controllers;

import lombok.RequiredArgsConstructor;
import mediaservice.services.ContentViewHistoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/history")
@RequiredArgsConstructor
public class ContentViewHistoryController {

    private final ContentViewHistoryService historyService;

    @PostMapping
    public ResponseEntity<Void> recordView(
            @RequestHeader("x-user-id") String accountId,
            @RequestParam String contentId) {
        historyService.recordView(accountId, contentId);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<Page<Object>> getViewHistory(
            @RequestHeader("x-user-id") String accountId,
            Pageable pageable) {
        Page<Object> result = historyService.getViewHistory(accountId, pageable);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping
    public ResponseEntity<Void> clearViewHistory(
            @RequestHeader("x-user-id") String accountId) {
        historyService.clearViewHistory(accountId);
        return ResponseEntity.ok().build();
    }
}
