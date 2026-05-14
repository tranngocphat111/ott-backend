package mediaservice.repositories;


import mediaservice.models.Mention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface MentionRepository extends JpaRepository<Mention, String> {

}
