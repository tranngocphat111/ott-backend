package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.models.Account;
import mediaservice.models.Content;
import mediaservice.models.Post;
import mediaservice.models.Story;
import mediaservice.models.SavedContent;
import mediaservice.repositories.ContentRepository;
import mediaservice.repositories.SavedContentRepository;
import mediaservice.repositories.AccountRepository;
import mediaservice.services.SavedContentService;
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
public class SavedContentServiceImpl implements SavedContentService {

    private final SavedContentRepository savedContentRepository;
    private final ContentRepository contentRepository;
    private final AccountRepository accountRepository;
    private final PostMapper postMapper;
    private final StoryMapper storyMapper;

    @Override
    @Transactional
    public void saveContent(String accountId, String contentId) {
        if (savedContentRepository.existsByAccountIdAndContentId(accountId, contentId)) {
            return;
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new RuntimeException("Content not found: " + contentId));

        SavedContent savedContent = SavedContent.builder()
                .account(account)
                .content(content)
                .build();
        
        savedContentRepository.save(savedContent);
    }

    @Override
    @Transactional
    public void unsaveContent(String accountId, String contentId) {
        savedContentRepository.findByAccountIdAndContentId(accountId, contentId)
                .ifPresent(savedContentRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSaved(String accountId, String contentId) {
        return savedContentRepository.existsByAccountIdAndContentId(accountId, contentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Object> getSavedContents(String accountId, Pageable pageable) {
        return savedContentRepository.findByAccountIdOrderBySavedAtDesc(accountId, pageable)
                .map(saved -> {
                    Content content = (Content) Hibernate.unproxy(saved.getContent());
                    if (content instanceof Post post) {
                        return postMapper.toResponse(post);
                    } else if (content instanceof Story story) {
                        return storyMapper.toResponse(story);
                    }
                    return null;
                });
    }
}
