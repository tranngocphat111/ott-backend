package mediaservice.repositories;

import mediaservice.models.CreatorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CreatorProfileRepository extends JpaRepository<CreatorProfile, String> {

}
