package mediaservice.controllers;

import lombok.RequiredArgsConstructor;
import mediaservice.services.SavedContentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/saved")
@RequiredArgsConstructor
public class SavedContentController {

    private final SavedContentService savedContentService;

    @PostMapping
    public ResponseEntity<Void> saveContent(
            @RequestHeader("x-user-id") String accountId,
            @RequestParam String contentId) {
        savedContentService.saveContent(accountId, contentId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> unsaveContent(
            @RequestHeader("x-user-id") String accountId,
            @RequestParam String contentId) {
        savedContentService.unsaveContent(accountId, contentId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/check")
    public ResponseEntity<Boolean> isSaved(
            @RequestHeader("x-user-id") String accountId,
            @RequestParam String contentId) {
        boolean saved = savedContentService.isSaved(accountId, contentId);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<Page<Object>> getSavedContents(
            @RequestHeader("x-user-id") String accountId,
            Pageable pageable) {
        Page<Object> result = savedContentService.getSavedContents(accountId, pageable);
        return ResponseEntity.ok(result);
    }
}
