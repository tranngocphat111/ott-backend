package mediaservice.repositories;

import mediaservice.models.Account;
import mediaservice.models.Content;
import mediaservice.models.ContentAccessControl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContentAccessControlRepository extends JpaRepository<ContentAccessControl, String> {
    List<ContentAccessControl> findByContent(Content content);
    List<ContentAccessControl> findByAccount(Account account);
    List<ContentAccessControl> findByContentAndAccount(Content content, Account account);
}

