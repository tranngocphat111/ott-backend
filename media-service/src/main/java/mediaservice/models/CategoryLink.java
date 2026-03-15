package mediaservice.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import mediaservice.models.enums.CategoryLinkTargetType;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Table(name = "category_links")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
public class CategoryLink {
    @GeneratedValue(strategy = GenerationType.UUID)
    @Id
    private String id;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    private String targetId;

    @Enumerated(EnumType.STRING)
    private CategoryLinkTargetType targetType;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
