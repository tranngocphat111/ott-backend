package iuh.fit.authservice.repository;

import iuh.fit.authservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findById(String id);

    Optional<User> findByGoogleId(String googleId);

}
