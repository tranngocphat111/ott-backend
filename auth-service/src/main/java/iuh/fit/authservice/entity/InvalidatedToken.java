package iuh.fit.authservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "invalidated_tokens", indexes = {
        @Index(name = "idx_invalidated_expiry", columnList = "expiry_time"),
        @Index(name = "idx_invalidated_user", columnList = "user_id")
})
public class InvalidatedToken {
    @Id
    String id;

    @Column(name = "expiry_time", nullable = false)
    LocalDateTime expiryTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    User user;

    @Column(name = "token_type", length = 20)
    String tokenType;

    @CreationTimestamp
    @Column(name = "invalidated_at", nullable = false, updatable = false)
    LocalDateTime invalidatedAt;

    @Column(name = "reason", columnDefinition = "TEXT")
    String reason;
}