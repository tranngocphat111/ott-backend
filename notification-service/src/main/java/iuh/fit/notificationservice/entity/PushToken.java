package iuh.fit.notificationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "push_tokens",
        indexes = {
                @Index(name = "idx_push_tokens_user_active", columnList = "user_id, active"),
                @Index(name = "idx_push_tokens_token", columnList = "expo_push_token")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_push_tokens_expo_token", columnNames = "expo_push_token")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "expo_push_token", nullable = false, unique = true, length = 255)
    private String expoPushToken;

    @Column(name = "platform", length = 32)
    private String platform;

    @Column(name = "device_id", length = 160)
    private String deviceId;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
