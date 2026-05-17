package mediaservice.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SavedContentService {
    void saveContent(String accountId, String contentId);
    void unsaveContent(String accountId, String contentId);
    boolean isSaved(String accountId, String contentId);
    Page<Object> getSavedContents(String accountId, Pageable pageable);
}
