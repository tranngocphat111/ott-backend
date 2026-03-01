package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.UserAccountRequest;
import mediaservice.dtos.responses.UserAccountResponse;
import mediaservice.mappers.UserAccountMapper;
import mediaservice.models.UserAccount;
import mediaservice.repositories.UserAccountRepository;
import mediaservice.services.UserAccountService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final UserAccountMapper userAccountMapper;

    @Override
    @Transactional
    public UserAccountResponse createUserAccount(UserAccountRequest request) {
        UserAccount userAccount = userAccountMapper.toEntity(request);
        UserAccount savedUserAccount = userAccountRepository.save(userAccount);
        return userAccountMapper.toResponse(savedUserAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public UserAccountResponse getUserAccountById(String id) {
        UserAccount userAccount = userAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User account not found with id: " + id));
        return userAccountMapper.toResponse(userAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public UserAccountResponse getUserAccountByUsername(String username) {
        UserAccount userAccount = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User account not found with username: " + username));
        return userAccountMapper.toResponse(userAccount);
    }

    @Override
    @Transactional(readOnly = true)
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
    public UserAccountResponse updateUserAccount(String id, UserAccountRequest request) {
        UserAccount userAccount = userAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User account not found with id: " + id));
        userAccountMapper.updateEntity(request, userAccount);
        UserAccount updatedUserAccount = userAccountRepository.save(userAccount);
        return userAccountMapper.toResponse(updatedUserAccount);
    }

    @Override
    @Transactional
    public void deleteUserAccount(String id) {
        if (!userAccountRepository.existsById(id)) {
            throw new RuntimeException("User account not found with id: " + id);
        }
        userAccountRepository.deleteById(id);
    }
}

