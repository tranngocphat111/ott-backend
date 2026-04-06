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
    Optional<User> findByPhoneAndDeletedAtIsNull(String phone);

    boolean existsByPhoneAndDeletedAtIsNull(String phone);
    boolean existsByEmailAndDeletedAtIsNull(String email);
    boolean existsByGoogleIdAndDeletedAtIsNull(String googleId);
    
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.twoFactorAuth WHERE u.id = :id")
    Optional<User> findByIdWithTwoFactorAuth(@Param("id") String id);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.twoFactorAuth WHERE u.phone = :phone AND u.deletedAt IS NULL")
    Optional<User> findByPhoneWithTwoFactorAuth(@Param("phone") String phone);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.twoFactorAuth WHERE u.email = :email AND u.deletedAt IS NULL")
    Optional<User> findByEmailWithTwoFactorAuth(@Param("email") String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.twoFactorAuth WHERE u.googleId = :googleId AND u.deletedAt IS NULL")
    Optional<User> findByGoogleIdWithTwoFactorAuth(@Param("googleId") String googleId);
}