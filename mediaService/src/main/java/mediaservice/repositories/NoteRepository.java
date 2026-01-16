package mediaservice.repositories;

import mediaservice.models.Note;
import mediaservice.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface NoteRepository extends JpaRepository<Note, String> {
}
