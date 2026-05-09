package mediaservice.models;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Set;

@Table(name = "accounts")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public abstract class Account {
    @Id
//    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String username;
    private String displayName;

    private String email;
    private String phoneNumber;

    private String avatarUrl;
    private String coverUrl;

    private String bio;

    @OneToMany(mappedBy = "account")
    private Set<Comment> comments;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;



}
