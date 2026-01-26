package iuh.fit.ottbackend.repository;

import feign.Param;
import iuh.fit.ottbackend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.googleId = :googleId ORDER BY u.createdAt DESC")
    List<User> findAllByGoogleId(@Param("googleId") String googleId);

    boolean existsByGoogleId(String googleId);


}