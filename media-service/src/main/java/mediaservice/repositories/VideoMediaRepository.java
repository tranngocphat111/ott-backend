package mediaservice.repositories;

import mediaservice.models.VideoMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoMediaRepository extends JpaRepository<VideoMedia, String> {
}

