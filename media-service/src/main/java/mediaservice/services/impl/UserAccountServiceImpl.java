package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.UserAccountRequest;
import mediaservice.dtos.responses.UserAccountResponse;
import mediaservice.mappers.UserAccountMapper;
import mediaservice.models.UserAccount;
import mediaservice.repositories.UserAccountRepository;
import mediaservice.services.S3Service;
import mediaservice.services.UserAccountService;
import mediaservice.services.UserEventPublisher;
import mediaservice.services.UserSyncService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final UserAccountMapper userAccountMapper;
    private final S3Service s3Service;
    private final UserEventPublisher userEventPublisher;
    private final UserSyncService userSyncService;

    @Override
    @Transactional
    @CacheEvict(value = {"users", "allUsers"}, allEntries = true)
    public UserAccountResponse createUserAccount(UserAccountRequest request) {
        UserAccount userAccount = userAccountMapper.toEntity(request);
        UserAccount savedUserAccount = userAccountRepository.save(userAccount);
        return userAccountMapper.toResponse(savedUserAccount);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#id", unless = "#result == null")
    public UserAccountResponse getUserAccountById(String id) {
        UserAccount user = userAccountRepository.findById(id).orElse(null);
        
        // Nếu không có user hoặc user chưa có thông tin (displayName null), thử sync
        if (user == null || user.getDisplayName() == null) {
            user = userSyncService.syncUser(id).orElse(user);
        }

        if (user == null) {
            throw new RuntimeException("User account not found with id: " + id);
        }
        
        return userAccountMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "'username:' + #username", unless = "#result == null")
    public UserAccountResponse getUserAccountByUsername(String username) {
        UserAccount userAccount = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User account not found with username: " + username));
        return userAccountMapper.toResponse(userAccount);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "allUsers", unless = "#result == null || #result.isEmpty()")
    public List<UserAccountResponse> getAllUserAccounts() {
        List<UserAccount> userAccounts = userAccountRepository.findAll();
        return userAccountMapper.toResponseList(userAccounts);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserAccountResponse> getAllUserAccounts(Pageable pageable) {
        Page<UserAccount> userAccounts = userAccountRepository.findAll(pageable);
        return userAccounts.map(userAccountMapper::toResponse);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "users", key = "#id"),
        @CacheEvict(value = "allUsers", allEntries = true)
    })
    public UserAccountResponse updateUserAccount(String id, UserAccountRequest request) {
        UserAccount userAccount = userAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User account not found with id: " + id));

        // MapStruct updateEntity (handles fields it knows about at compile-time)
        userAccountMapper.updateEntity(request, userAccount);

        // Explicitly apply profile fields – đảm bảo luôn được persist
        // kể cả khi MapStruct chưa được recompile với các field mới.
        // Note: send empty string "" to clear a field; null means "don't touch".
        if (request.getBio()                != null) userAccount.setBio(request.getBio());
        if (request.getWork()               != null) userAccount.setWork(request.getWork());
        if (request.getLocation()           != null) userAccount.setLocation(request.getLocation());
        if (request.getRelationshipStatus() != null) userAccount.setRelationshipStatus(request.getRelationshipStatus());

        UserAccount updated = userAccountRepository.saveAndFlush(userAccount);

        // Broadcast update
        userEventPublisher.publishUserUpdated(mediaservice.dtos.events.UserUpdatedEvent.builder()
                .userId(id)
                .avatar(updated.getAvatarUrl())
                .coverUrl(updated.getCoverUrl())
                .displayName(updated.getDisplayName())
                .bio(updated.getBio())
                .build());

        return userAccountMapper.toResponse(updated);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"users", "allUsers"}, key = "#id")
    public void deleteUserAccount(String id) {
        if (!userAccountRepository.existsById(id)) {
            throw new RuntimeException("User account not found with id: " + id);
        }
        userAccountRepository.deleteById(id);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "users", key = "#id"),
        @CacheEvict(value = "allUsers", allEntries = true)
    })
    public UserAccountResponse uploadAvatar(String id, MultipartFile file) {
        UserAccount userAccount = userAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User account not found with id: " + id));
        String s3Key = s3Service.uploadFile(file, "avatars");
        String avatarUrl = s3Service.getFullUrl(s3Key);
        userAccount.setAvatarUrl(avatarUrl);
        UserAccount updated = userAccountRepository.saveAndFlush(userAccount);

        // Broadcast update
        userEventPublisher.publishUserUpdated(mediaservice.dtos.events.UserUpdatedEvent.builder()
                .userId(id)
                .avatar(updated.getAvatarUrl())
                .coverUrl(updated.getCoverUrl())
                .displayName(updated.getDisplayName())
                .bio(updated.getBio())
                .build());

        return userAccountMapper.toResponse(updated);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "users", key = "#id"),
        @CacheEvict(value = "allUsers", allEntries = true)
    })
    public UserAccountResponse uploadCover(String id, MultipartFile file) {
        UserAccount userAccount = userAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User account not found with id: " + id));
        String s3Key = s3Service.uploadFile(file, "covers");
        String coverUrl = s3Service.getFullUrl(s3Key);
        userAccount.setCoverUrl(coverUrl);
        UserAccount updated = userAccountRepository.saveAndFlush(userAccount);

        // Broadcast update
        userEventPublisher.publishUserUpdated(mediaservice.dtos.events.UserUpdatedEvent.builder()
                .userId(id)
                .avatar(updated.getAvatarUrl())
                .coverUrl(updated.getCoverUrl())
                .displayName(updated.getDisplayName())
                .bio(updated.getBio())
                .build());

        return userAccountMapper.toResponse(updated);
    }
}

