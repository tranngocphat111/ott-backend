package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.models.Account;
import mediaservice.models.Content;
import mediaservice.models.ContentViewHistory;
import mediaservice.models.Post;
import mediaservice.models.Story;
import mediaservice.repositories.AccountRepository;
import mediaservice.repositories.ContentRepository;
import mediaservice.repositories.ContentViewHistoryRepository;
import mediaservice.services.ContentViewHistoryService;
import mediaservice.mappers.PostMapper;
import mediaservice.mappers.StoryMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.hibernate.Hibernate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentViewHistoryServiceImpl implements ContentViewHistoryService {

    private final ContentViewHistoryRepository historyRepository;
    private final ContentRepository contentRepository;
    private final AccountRepository accountRepository;
    private final PostMapper postMapper;
    private final StoryMapper storyMapper;

    @Override
    @Transactional
    public void recordView(String accountId, String contentId) {
        historyRepository.findByAccountIdAndContentId(accountId, contentId)
                .ifPresentOrElse(
                        history -> {
                            // Cập nhật lại thời gian xem mới nhất (UpdateTimestamp)
                            history.setViewedAt(java.time.LocalDateTime.now());
                            historyRepository.save(history);
                        },
                        () -> {
                            Account account = accountRepository.findById(accountId)
                                    .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));
                            Content content = contentRepository.findById(contentId)
                                    .orElseThrow(() -> new RuntimeException("Content not found: " + contentId));

                            ContentViewHistory newHistory = ContentViewHistory.builder()
                                    .account(account)
                                    .content(content)
                                    .build();
                            historyRepository.save(newHistory);
                        }
                );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Object> getViewHistory(String accountId, Pageable pageable) {
        return historyRepository.findByAccountIdOrderByViewedAtDesc(accountId, pageable)
                .map(history -> {
                    Content content = (Content) Hibernate.unproxy(history.getContent());
                    if (content instanceof Post post) {
                        return postMapper.toResponse(post);
                    } else if (content instanceof Story story) {
                        return storyMapper.toResponse(story);
                    }
                    return null;
                });
    }

    @Override
    @Transactional
    public void clearViewHistory(String accountId) {
        historyRepository.deleteByAccountId(accountId);
    }
}
