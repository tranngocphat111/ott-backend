package mediaservice.models;


import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Table(name = "text_items")

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = false)
@DiscriminatorValue("TEXT_ITEM")
public class TextItem extends StoryItem{
    @Column(columnDefinition = "TEXT")
    private String content;
    private String font;
    private String color;
    private String backgroundColor;
    private String alignment;
}
