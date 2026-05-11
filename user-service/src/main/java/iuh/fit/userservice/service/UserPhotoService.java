package iuh.fit.userservice.service;

import iuh.fit.userservice.config.AwsS3Properties;
import iuh.fit.userservice.dto.request.AddPhotoRequest;
import iuh.fit.userservice.dto.response.PhotoListResponse;
import iuh.fit.userservice.dto.response.UserPhotoResponse;
import iuh.fit.userservice.entity.UserPhoto;
import iuh.fit.userservice.entity.User;
import iuh.fit.userservice.entity.enums.PhotoType;
import iuh.fit.userservice.exception.AppException;
import iuh.fit.userservice.exception.ErrorCode;
import iuh.fit.userservice.repository.UserPhotoRepository;
import iuh.fit.userservice.repository.UserRepository;
import iuh.fit.userservice.utils.UserValidationUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserPhotoService {

    private static final int MAX_PHOTOS_PER_TYPE = 10;

    private final UserPhotoRepository photoRepository;
    private final UserValidationUtil userValidationUtil;
    private final AwsS3Properties props;
    private final S3Client s3Client;
    private final UserRepository userRepository;
    private final UserEventPublisher userEventPublisher;

    public PhotoListResponse getAllPhotos(String userId) {
        List<UserPhotoResponse> avatars = toResponseList(
                photoRepository.findByUserIdAndPhotoTypeOrderByCreatedAtDesc(
                        userId, PhotoType.AVATAR));
        List<UserPhotoResponse> covers = toResponseList(
                photoRepository.findByUserIdAndPhotoTypeOrderByCreatedAtDesc(
                        userId, PhotoType.COVER));

        User user = userValidationUtil.getUserById(userId);
        return PhotoListResponse.builder()
                .avatars(avatars)
                .covers(covers)
                .activeAvatarUrl(user.getAvatarUrl())
                .activeCoverUrl(user.getCoverUrl())
                .build();
    }

    @Transactional
    public UserPhotoResponse addPhoto(String userId, AddPhotoRequest request) {
        userValidationUtil.getUserById(userId); // validate tồn tại

        long count = photoRepository.countByUserIdAndPhotoType(
                userId, request.getPhotoType());
        if (count >= MAX_PHOTOS_PER_TYPE) {
            log.warn("Photo limit reached | userId: {} | type: {} | count: {}",
                    userId, request.getPhotoType(), count);
            throw new AppException(ErrorCode.PHOTO_LIMIT_EXCEEDED);
        }

        UserPhoto photo = UserPhoto.builder()
                .userId(userId)
                .url(request.getFileUrl())
                .s3Key(request.getS3Key())
                .photoType(request.getPhotoType())
                .isActive(false) // chưa active, cần gọi setActive riêng
                .build();

        photo = photoRepository.save(photo);
        log.info("Photo added | userId: {} | type: {} | photoId: {}",
                userId, request.getPhotoType(), photo.getId());
        return toResponse(photo);
    }

    // ─── Set ảnh active (đang dùng) ──────────────────────────────────────────

    @Transactional
    public UserPhotoResponse setActivePhoto(String userId, String photoId) {
        UserPhoto photo = photoRepository.findByIdAndUserId(photoId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.PHOTO_NOT_FOUND));

        PhotoType type = photo.getPhotoType();

        // Deactivate tất cả ảnh cùng loại
        photoRepository.deactivateAll(userId, type);

        // Activate ảnh được chọn
        photo.setIsActive(true);
        photoRepository.save(photo);

        // Cập nhật User.avatarUrl / coverUrl
        User user = userValidationUtil.getUserById(userId);
        if (type == PhotoType.AVATAR) {
            user.setAvatarUrl(photo.getUrl());
        } else {
            user.setCoverUrl(photo.getUrl());
        }
        userValidationUtil.userRepository.save(user);


        userEventPublisher.publishUserUpdated(
                iuh.fit.userservice.dto.event.UserUpdatedEvent.builder()
                        .userId(user.getId())
                        .fullName(user.getFullName())
                        .avatarUrl(user.getAvatarUrl())
                        .coverUrl(user.getCoverUrl())
                        .bio(user.getBio())
                        .work(user.getWork())
                        .location(user.getLocation())
                        .relationshipStatus(user.getRelationshipStatus())
                        .email(user.getEmail())
                        .phone(user.getPhone())
                        .build());

        log.info("Active photo set | userId: {} | type: {} | photoId: {}", userId, type, photoId);
        return toResponse(photo);
    }

    // ─── Xóa ảnh ─────────────────────────────────────────────────────────────

    @Transactional
    public void deletePhoto(String userId, String photoId) {
        UserPhoto photo = findOwnedPhoto(userId, photoId);

        deletePhotoInternal(userId, photo);
    }

    @Transactional
    public void deletePhotoByType(String userId, PhotoType photoType, String photoId) {
        UserPhoto photo = findOwnedPhoto(userId, photoId);
        if (photo.getPhotoType() != photoType) {
            throw new AppException(ErrorCode.PHOTO_NOT_FOUND);
        }

        deletePhotoInternal(userId, photo);
    }

    private void deletePhotoInternal(String userId, UserPhoto photo) {
        PhotoType type = photo.getPhotoType();
        boolean wasActive = Boolean.TRUE.equals(photo.getIsActive());

        // Hard delete trước
        photoRepository.delete(photo);
        deleteFromS3(photo.getS3Key());

        log.info("Photo deleted | userId: {} | photoId: {} | wasActive: {}", userId, photo.getId(), wasActive);

        if (wasActive) {

            List<UserPhoto> remaining = photoRepository
                    .findByUserIdAndPhotoTypeOrderByCreatedAtDesc(userId, type);

            User user = userValidationUtil.getUserById(userId);
            String defaultUrl = type == PhotoType.AVATAR
                    ? props.getDefaultAvatar()
                    : props.getDefaultCoverPhoto();

            if (remaining.isEmpty()) {
                if (type == PhotoType.AVATAR)
                    user.setAvatarUrl(defaultUrl);
                else
                    user.setCoverUrl(defaultUrl);
                log.info("No photos left, reset to default | userId: {} | type: {}", userId, type);
            } else {
                UserPhoto next = remaining.get(0);
                next.setIsActive(true);
                photoRepository.save(next);

                if (type == PhotoType.AVATAR)
                    user.setAvatarUrl(next.getUrl());
                else
                    user.setCoverUrl(next.getUrl());
                log.info("Auto-activated next photo | userId: {} | photoId: {}", userId, next.getId());
            }

            userRepository.save(user);

            userEventPublisher.publishUserUpdated(
                    iuh.fit.userservice.dto.event.UserUpdatedEvent.builder()
                            .userId(user.getId())
                            .fullName(user.getFullName())
                            .avatarUrl(user.getAvatarUrl())
                            .coverUrl(user.getCoverUrl())
                            .bio(user.getBio())
                            .work(user.getWork())
                            .location(user.getLocation())
                            .relationshipStatus(user.getRelationshipStatus())
                            .email(user.getEmail())
                            .phone(user.getPhone())
                            .build());
        }
    }

    @Transactional
    public void removeActivePhotoByType(String userId, PhotoType type) {
        Optional<UserPhoto> activePhoto = photoRepository
                .findByUserIdAndPhotoTypeAndIsActiveTrue(userId, type);

        if (activePhoto.isEmpty()) {
            log.info("No active {} photo to remove | userId: {}", type, userId);
            return;
        }

        deletePhoto(userId, activePhoto.get().getId());
    }

    private UserPhoto findOwnedPhoto(String userId, String photoId) {
        return photoRepository.findByIdAndUserId(photoId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.PHOTO_NOT_FOUND));
    }

    @Transactional
    public UserPhotoResponse addAndSetActive(String userId, AddPhotoRequest request) {
        UserPhotoResponse added = addPhoto(userId, request);
        return setActivePhoto(userId, added.getId());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void deleteFromS3(String s3Key) {
        if (s3Key == null || s3Key.isBlank())
            return;
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(s3Key)
                    .build());
            log.info("S3 object deleted | key: {}", s3Key);
        } catch (Exception e) {
            log.warn("Failed to delete S3 object | key: {} | reason: {}", s3Key, e.getMessage());
        }
    }

    private UserPhotoResponse toResponse(UserPhoto p) {
        return UserPhotoResponse.builder()
                .id(p.getId()).url(p.getUrl()).s3Key(p.getS3Key())
                .photoType(p.getPhotoType()).isActive(p.getIsActive())
                .createdAt(p.getCreatedAt()).build();
    }

    private List<UserPhotoResponse> toResponseList(List<UserPhoto> photos) {
        return photos.stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deleteAllUserPhotos(String userId) {
        List<UserPhoto> allPhotos = photoRepository.findByUserId(userId);

        String defaultAvatar = props.getDefaultAvatar();
        String defaultCover = props.getDefaultCoverPhoto();

        for (UserPhoto photo : allPhotos) {
            if (photo.getUrl().equals(defaultAvatar) || photo.getUrl().equals(defaultCover)) {
                continue;
            }
            deleteFromS3(photo.getS3Key());
            photoRepository.delete(photo);
        }

        log.info("All photos deleted for userId: {} | total: {}", userId, allPhotos.size());
    }
}