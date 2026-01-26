package mediaservice.repositories;

import mediaservice.models.MediaMusic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface MediaMusicRepository extends JpaRepository<MediaMusic, String> {

}
