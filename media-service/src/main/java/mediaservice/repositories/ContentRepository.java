package mediaservice.repositories;

import mediaservice.models.Content;
import mediaservice.models.enums.ContentStatusType;
import mediaservice.models.enums.ContentTargetType;
import mediaservice.models.enums.VisibilityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContentRepository extends JpaRepository<Content, String> {
    List<Content> findByStatus(ContentStatusType status);
    List<Content> findByVisibility(VisibilityType visibility);
}

