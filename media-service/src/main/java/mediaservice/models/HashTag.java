package mediaservice.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Table(name = "hashtags")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
public class HashTag {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name;

    @ManyToMany(mappedBy = "hashTags")
    private Set<Content> contents;

}
