
package iuh.fit.userservice.entity;

import iuh.fit.userservice.entity.enums.PhotoType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_photos", indexes = {
        @Index(name = "idx_user_photos_user_type", columnList = "user_id, photo_type"),
        @Index(name = "idx_user_photos_active", columnList = "user_id, photo_type, is_active")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    @Enumerated(EnumType.STRING)
    @Column(name = "photo_type", nullable = false)
    private PhotoType photoType;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

}