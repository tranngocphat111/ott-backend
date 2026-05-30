package mediaservice.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import mediaservice.models.enums.RuleType;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;


import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ContentAccessControl {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private String id;

    @ManyToOne
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne
    @JoinColumn(name = "content_id")
    private Content content;

    @Enumerated(EnumType.STRING)
    private RuleType ruleType;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
