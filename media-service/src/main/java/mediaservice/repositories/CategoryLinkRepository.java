package mediaservice.repositories;

import mediaservice.models.CategoryLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface CategoryLinkRepository extends JpaRepository<CategoryLink, String> {

}
