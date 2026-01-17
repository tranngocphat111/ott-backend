package mediaservice.repositories;

import mediaservice.models.ContentAccessControll;
import mediaservice.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface ContentAccessControllRepository extends JpaRepository<ContentAccessControll, String> {
}
