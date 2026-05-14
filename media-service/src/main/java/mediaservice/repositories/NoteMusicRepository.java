package mediaservice.repositories;


import mediaservice.models.NoteMusic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface NoteMusicRepository extends JpaRepository<NoteMusic, String> {

}
