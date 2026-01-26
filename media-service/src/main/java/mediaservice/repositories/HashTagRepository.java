package mediaservice.repositories;

import mediaservice.models.HashTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface HashTagRepository extends JpaRepository<HashTag, String> {
}
