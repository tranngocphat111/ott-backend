package mediaservice.models;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Table(name = "image_medias")

@Entity
@EqualsAndHashCode(callSuper = false)
@DiscriminatorValue("IMAGE_MEDIA")
@ToString(callSuper = true)
public class ImageMedia extends Media {
}
