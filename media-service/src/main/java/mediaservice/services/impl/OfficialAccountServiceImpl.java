package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.OfficialAccountRequest;
import mediaservice.dtos.responses.OfficialAccountResponse;
import mediaservice.mappers.OfficialAccountMapper;
import mediaservice.models.OfficialAccount;
import mediaservice.repositories.OfficialAccountRepository;
import mediaservice.services.OfficialAccountService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OfficialAccountServiceImpl implements OfficialAccountService {

    private final OfficialAccountRepository officialAccountRepository;
    private final OfficialAccountMapper officialAccountMapper;

    @Override
    @Transactional
    public OfficialAccountResponse createOfficialAccount(OfficialAccountRequest request) {
        OfficialAccount officialAccount = officialAccountMapper.toEntity(request);
        OfficialAccount savedOfficialAccount = officialAccountRepository.save(officialAccount);
        return officialAccountMapper.toResponse(savedOfficialAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public OfficialAccountResponse getOfficialAccountById(String id) {
        OfficialAccount officialAccount = officialAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Official account not found with id: " + id));
        return officialAccountMapper.toResponse(officialAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OfficialAccountResponse> getAllOfficialAccounts() {
        List<OfficialAccount> officialAccounts = officialAccountRepository.findAll();
        return officialAccountMapper.toResponseList(officialAccounts);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OfficialAccountResponse> getAllOfficialAccounts(Pageable pageable) {
        Page<OfficialAccount> officialAccounts = officialAccountRepository.findAll(pageable);
        return officialAccounts.map(officialAccountMapper::toResponse);
    }

    @Override
    @Transactional
    public OfficialAccountResponse updateOfficialAccount(String id, OfficialAccountRequest request) {
        OfficialAccount officialAccount = officialAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Official account not found with id: " + id));
        officialAccountMapper.updateEntity(request, officialAccount);
        OfficialAccount updatedOfficialAccount = officialAccountRepository.save(officialAccount);
        return officialAccountMapper.toResponse(updatedOfficialAccount);
    }

    @Override
    @Transactional
    public void deleteOfficialAccount(String id) {
        if (!officialAccountRepository.existsById(id)) {
            throw new RuntimeException("Official account not found with id: " + id);
        }
        officialAccountRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OfficialAccountResponse> getOfficialAccountsByOwnerId(String ownerId) {
        List<OfficialAccount> officialAccounts = officialAccountRepository.findAll(); // TODO: Add custom query
        return officialAccountMapper.toResponseList(officialAccounts);
    }
}
