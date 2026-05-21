package iuh.fit.notificationservice.controller;

import iuh.fit.notificationservice.entity.InAppNotification;
import iuh.fit.notificationservice.service.InAppNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications/inapp")
@RequiredArgsConstructor
public class InAppNotificationController {

    private final InAppNotificationService service;

    @GetMapping("/{recipientId}")
    public ResponseEntity<List<InAppNotification>> getNotifications(@PathVariable String recipientId) {
        return ResponseEntity.ok(service.getNotifications(recipientId));
    }

    @GetMapping("/{recipientId}/unread")
    public ResponseEntity<List<InAppNotification>> getUnreadNotifications(@PathVariable String recipientId) {
        return ResponseEntity.ok(service.getUnreadNotifications(recipientId));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long notificationId) {
        service.markAsRead(notificationId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{recipientId}/read-all")
    public ResponseEntity<Void> markAllAsRead(@PathVariable String recipientId) {
        service.markAllAsRead(recipientId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long notificationId) {
        service.deleteNotification(notificationId);
        return ResponseEntity.ok().build();
    }
}

