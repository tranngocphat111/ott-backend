package mediaservice.repositories;


import mediaservice.models.ImageItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface ImageItemRepository extends JpaRepository<ImageItem, String> {
}
