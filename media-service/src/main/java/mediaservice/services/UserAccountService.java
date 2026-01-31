package mediaservice.services;

import mediaservice.dtos.requests.UserAccountRequest;
import mediaservice.dtos.responses.UserAccountResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserAccountService {
    UserAccountResponse createUserAccount(UserAccountRequest request);
    UserAccountResponse getUserAccountById(String id);
    UserAccountResponse getUserAccountByUsername(String username);
    List<UserAccountResponse> getAllUserAccounts();
    Page<UserAccountResponse> getAllUserAccounts(Pageable pageable);
    UserAccountResponse updateUserAccount(String id, UserAccountRequest request);
    void deleteUserAccount(String id);
}

