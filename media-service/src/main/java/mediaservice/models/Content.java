package mediaservice.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import mediaservice.models.enums.VisibilityType;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Table(name = "contents")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "content_type")


@Entity
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Data
public abstract class Content {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

//    chủ bài đăng
    @ManyToOne
    @JsonIgnore
    private User user;

    @ManyToMany
    @JoinTable(name = "mentioned_users",
            joinColumns = @JoinColumn(name = "content_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @JsonIgnore
    private Set<User> mentionedUsers;


//   những content phụ
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

//  mặc định là public
    private VisibilityType visibility = VisibilityType.PUBLIC;

    @Column(name = "total_reactions_count")
    private int totalReactionsCount;

    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL)
    @JsonIgnore
    private Set<Reaction> reactions;

    @Column(name = "comments_count")
    private int commentsCount;

    @OneToMany(mappedBy = "content")
    @JsonIgnore
    private Set<Comment> comments;


    @Column(name = "share_count")
    private int shareCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
