package iuh.fit.userservice.entity;

import iuh.fit.userservice.entity.enums.AccountType;
import iuh.fit.userservice.entity.enums.Gender;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_phone",     columnNames = "phone"),
                @UniqueConstraint(name = "uk_user_email",     columnNames = "email"),
                @UniqueConstraint(name = "uk_user_google_id", columnNames = "google_id")
        },
        indexes = {
                @Index(name = "idx_users_phone",        columnList = "phone"),
                @Index(name = "idx_users_email",        columnList = "email"),
                @Index(name = "idx_users_google_id",    columnList = "google_id"),
                @Index(name = "idx_users_account_type", columnList = "account_type"),
                @Index(name = "idx_users_is_active",    columnList = "is_active")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, length = 50)
    private String phone;

    @Column(unique = true)
    private String email;

    @Column(name = "google_id", unique = true, length = 150)
    private String googleId;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    @Builder.Default
    private AccountType accountType = AccountType.USER;

    @Column(name = "is_phone_verified", nullable = false)
    @Builder.Default
    private Boolean isPhoneVerified = true;

    @Column(name = "is_email_verified", nullable = false)
    @Builder.Default
    private Boolean isEmailVerified = true;

    @Column(name = "phone_verified_at")
    private LocalDateTime phoneVerifiedAt;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "email_changed_at")
    private LocalDateTime emailChangedAt;

    @Column(name = "phone_changed_at")
    private LocalDateTime phoneChangedAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_blocked")
    @Builder.Default
    private Boolean isBlocked = false;

    @Column(name = "blocked_until")
    private LocalDateTime blockedUntil;

    @Column(name = "blocked_reason", columnDefinition = "TEXT")
    private String blockedReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "is_first_login")
    @Builder.Default
    private Boolean isFirstLogin = true;

    @Column(name = "welcome_email_sent")
    @Builder.Default
    private Boolean welcomeEmailSent = false;

    @Column(name = "welcome_email_sent_at")
    private LocalDateTime welcomeEmailSentAt;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private TwoFactorAuth twoFactorAuth;
}