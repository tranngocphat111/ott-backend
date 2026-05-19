package mediaservice.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "content_view_history", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"account_id", "content_id"})
})
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContentViewHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id")
    private Content content;

    @UpdateTimestamp
    private LocalDateTime viewedAt;
}
