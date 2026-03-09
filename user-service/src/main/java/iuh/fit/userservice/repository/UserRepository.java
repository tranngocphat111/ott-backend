package iuh.fit.userservice.repository;

import iuh.fit.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByPhone(String phone);
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailAndDeletedAtIsNull(String email);
    Optional<User> findByPhoneAndDeletedAtIsNull(String phone);
    Optional<User> findByGoogleIdAndDeletedAtIsNull(String googleId);

    boolean existsByPhoneAndDeletedAtIsNull(String phone);
    boolean existsByEmailAndDeletedAtIsNull(String email);
    boolean existsByEmailAndDeletedAtIsNullAndIdNot(String email, String id);
    boolean existsByGoogleIdAndDeletedAtIsNull(String googleId);

    @Query("""
        SELECT COUNT(u) FROM User u
        WHERE u.deletedAt IS NOT NULL
        AND (u.phone LIKE CONCAT(:phone, '_deleted_%')
             OR u.email LIKE CONCAT(:email, '_deleted_%'))
        """)
    long countDeletedAccountsByPhoneOrEmail(@Param("phone") String phone, @Param("email") String email);

    @Query("""
        SELECT u FROM User u
        WHERE u.deletedAt IS NOT NULL
        AND u.deletedAt < :cutoffDate
        AND u.phone LIKE CONCAT(:phone, '_deleted_%')
        """)
    Optional<User> findExpiredDeletedUserByPhone(@Param("phone") String phone, @Param("cutoffDate") LocalDateTime cutoffDate);
}