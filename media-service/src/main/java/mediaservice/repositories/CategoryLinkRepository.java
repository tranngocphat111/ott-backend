package mediaservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface CategoryLinkRepository extends JpaRepository<CategoryLinkRepository, String> {

}
