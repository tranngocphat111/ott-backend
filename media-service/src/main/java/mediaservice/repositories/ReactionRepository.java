package mediaservice.repositories;

import mediaservice.models.Reaction;
import mediaservice.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface ReactionRepository extends JpaRepository<Reaction, String> {
}
