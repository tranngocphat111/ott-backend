package mediaservice.repositories;

import mediaservice.models.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MediaRepository extends JpaRepository<Media, String> {

    @Query("select account.id from Media media join media.content content join content.account account where media.id = :mediaId")
    Optional<String> findUploaderIdByMediaId(@Param("mediaId") String mediaId);
}
