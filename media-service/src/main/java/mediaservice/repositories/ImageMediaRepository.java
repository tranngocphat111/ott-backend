package mediaservice.repositories;

import mediaservice.models.ImageMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImageMediaRepository extends JpaRepository<ImageMedia, String> {
}

