package iuh.fit.userservice.repository;

import iuh.fit.userservice.entity.UserPhoto;
import iuh.fit.userservice.entity.enums.PhotoType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPhotoRepository extends JpaRepository<UserPhoto, String> {

    List<UserPhoto> findByUserIdAndPhotoTypeOrderByCreatedAtDesc(String userId, PhotoType type);

    long countByUserIdAndPhotoType(String userId, PhotoType type);

    Optional<UserPhoto> findByIdAndUserId(String id, String userId);

    Optional<UserPhoto> findByUserIdAndPhotoTypeAndIsActiveTrue(String userId, PhotoType type);

    @Modifying
    @Query("UPDATE UserPhoto p SET p.isActive = false WHERE p.userId = :userId AND p.photoType = :type")
    void deactivateAll(@Param("userId") String userId, @Param("type") PhotoType type);

    List<UserPhoto> findByUserId(String userId);
}