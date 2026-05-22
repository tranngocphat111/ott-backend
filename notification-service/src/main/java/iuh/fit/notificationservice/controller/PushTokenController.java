package iuh.fit.notificationservice.controller;

import iuh.fit.notificationservice.dto.request.PushTokenRequest;
import iuh.fit.notificationservice.service.PushNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications/push-tokens")
@RequiredArgsConstructor
public class PushTokenController {
    private final PushNotificationService pushNotificationService;

    @PostMapping
    public ResponseEntity<Void> register(@Valid @RequestBody PushTokenRequest request) {
        pushNotificationService.registerToken(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/unregister")
    public ResponseEntity<Void> unregister(@Valid @RequestBody PushTokenRequest request) {
        pushNotificationService.unregisterToken(request);
        return ResponseEntity.ok().build();
    }
}
