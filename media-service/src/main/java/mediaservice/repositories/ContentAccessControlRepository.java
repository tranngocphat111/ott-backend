package mediaservice.repositories;

import mediaservice.models.Content;
import mediaservice.models.ContentAccessControl;
import mediaservice.models.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContentAccessControlRepository extends JpaRepository<ContentAccessControl, String> {
    List<ContentAccessControl> findByContent(Content content);
    List<ContentAccessControl> findByUser(UserAccount user);
    List<ContentAccessControl> findByContentAndUser(Content content, UserAccount user);
}

