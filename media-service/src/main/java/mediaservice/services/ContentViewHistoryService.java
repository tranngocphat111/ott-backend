package mediaservice.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ContentViewHistoryService {
    void recordView(String accountId, String contentId);
    Page<Object> getViewHistory(String accountId, Pageable pageable);
    void clearViewHistory(String accountId);
}
