package iuh.fit.ottbackend.entity;

import iuh.fit.ottbackend.entity.enums.EmailStatus;
import iuh.fit.ottbackend.entity.enums.EmailType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity để tracking tất cả email đã gửi
 * Giúp debug và tránh gửi duplicate email
 */
@Entity
@Table(name = "email_logs", indexes = {
        @Index(name = "idx_email_logs_user", columnList = "user_id"),
        @Index(name = "idx_email_logs_email", columnList = "email_to, email_type"),
        @Index(name = "idx_email_logs_status", columnList = "status"),
        @Index(name = "idx_email_logs_type", columnList = "email_type, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "email_to", nullable = false)
    private String emailTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_type", nullable = false, length = 30)
    private EmailType emailType;

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "template_name", length = 100)
    private String templateName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private EmailStatus status = EmailStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    public boolean isSent() {
        return status == EmailStatus.SENT;
    }
}