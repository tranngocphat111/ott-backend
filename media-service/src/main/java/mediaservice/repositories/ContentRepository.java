package mediaservice.repositories;

import mediaservice.models.Content;
import mediaservice.models.User;
import mediaservice.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentRepository extends JpaRepository<Content, String> {}
