package mediaservice.services;

import mediaservice.dtos.requests.OfficialAccountRequest;
import mediaservice.dtos.responses.OfficialAccountResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OfficialAccountService {
    OfficialAccountResponse createOfficialAccount(OfficialAccountRequest request);
    OfficialAccountResponse getOfficialAccountById(String id);
    List<OfficialAccountResponse> getAllOfficialAccounts();
    Page<OfficialAccountResponse> getAllOfficialAccounts(Pageable pageable);
    OfficialAccountResponse updateOfficialAccount(String id, OfficialAccountRequest request);
    void deleteOfficialAccount(String id);
    List<OfficialAccountResponse> getOfficialAccountsByOwnerId(String ownerId);
}

