package mediaservice.repositories;

import mediaservice.models.OfficialAccount;
import mediaservice.models.User;
import mediaservice.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OfficialAccountRepository extends JpaRepository<OfficialAccount, String> {

}
