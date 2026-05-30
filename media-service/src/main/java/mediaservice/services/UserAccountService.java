package mediaservice.services;

import mediaservice.dtos.requests.UserAccountRequest;
import mediaservice.dtos.responses.UserAccountResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserAccountService {
    UserAccountResponse createUserAccount(UserAccountRequest request);
    UserAccountResponse getUserAccountById(String id);
    UserAccountResponse getUserAccountByUsername(String username);
    List<UserAccountResponse> getAllUserAccounts();
    Page<UserAccountResponse> getAllUserAccounts(Pageable pageable);
    List<UserAccountResponse> searchUserAccounts(String query);
    Page<UserAccountResponse> searchUserAccounts(String query, Pageable pageable);
    UserAccountResponse updateUserAccount(String id, UserAccountRequest request);
    void deleteUserAccount(String id);
    UserAccountResponse uploadAvatar(String id, MultipartFile file);
    UserAccountResponse uploadCover(String id, MultipartFile file);
}

