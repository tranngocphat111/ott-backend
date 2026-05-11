package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.CommentRequest;
import mediaservice.dtos.responses.CommentResponse;
import mediaservice.mappers.CommentMapper;
import mediaservice.models.Account;
import mediaservice.models.Comment;
import mediaservice.models.Content;
import mediaservice.repositories.AccountRepository;
import mediaservice.repositories.CommentRepository;
import mediaservice.repositories.ContentRepository;
import mediaservice.services.CommentService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final CommentMapper commentMapper;
    private final AccountRepository accountRepository;
    private final ContentRepository contentRepository;
    private final mediaservice.realtime.PostActivityPublisher postActivityPublisher;
    private final mediaservice.services.UserSyncService userSyncService;

    @Override
    @Transactional
    @CacheEvict(value = "comments", key = "#request.contentId")
    public CommentResponse createComment(CommentRequest request) {
        Comment comment = new Comment();
        comment.setText(request.getText());
        comment.setDepth(request.getParentCommentId() == null ? 0 : 1);

        if (request.getAccountId() != null) {
            // Đảm bảo user đã được sync thông tin (displayName, avatar...)
            mediaservice.models.UserAccount account = userSyncService.syncUser(request.getAccountId()).orElse(null);
            comment.setAccount(account);
        }
        if (request.getContentId() != null) {
            Content content = contentRepository.findById(request.getContentId()).orElse(null);
            comment.setContent(content);
        }
        if (request.getParentCommentId() != null) {
            Comment parent = commentRepository.findById(request.getParentCommentId()).orElse(null);
            comment.setParentComment(parent);
        }
        Comment savedComment = commentRepository.save(comment);
        CommentResponse response = commentMapper.toResponse(savedComment);
        if (savedComment.getContent() != null) {
            postActivityPublisher.publish(savedComment.getContent().getId(), "COMMENT", "CREATE", response);
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public CommentResponse getCommentById(String id) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Comment not found with id: " + id));
        return commentMapper.toResponse(comment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getAllComments() {
        List<Comment> comments = commentRepository.findAll();
        return commentMapper.toResponseList(comments);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CommentResponse> getAllComments(Pageable pageable) {
        Page<Comment> comments = commentRepository.findAll(pageable);
        return comments.map(commentMapper::toResponse);
    }

    @Override
    @Transactional
    @CacheEvict(value = "comments", allEntries = true)
    public CommentResponse updateComment(String id, CommentRequest request) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Comment not found with id: " + id));
        if (request.getText() != null) {
            comment.setText(request.getText());
            comment.setEdited(true);
        }
        Comment updatedComment = commentRepository.save(comment);
        CommentResponse response = commentMapper.toResponse(updatedComment);
        if (updatedComment.getContent() != null) {
            postActivityPublisher.publish(updatedComment.getContent().getId(), "COMMENT", "UPDATE", response);
        }
        return response;
    }

    @Override
    @Transactional
    @CacheEvict(value = "comments", allEntries = true)
    public void deleteComment(String id) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Comment not found with id: " + id));
        comment.setDeleted(true);
        commentRepository.save(comment);
        if (comment.getContent() != null) {
            postActivityPublisher.publish(comment.getContent().getId(), "COMMENT", "DELETE", commentMapper.toResponse(comment));
        }
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "comments", key = "#contentId", unless = "#result == null || #result.isEmpty()")
    public List<CommentResponse> getCommentsByContentId(String contentId) {
        List<Comment> comments = commentRepository.findByContent_IdAndIsDeletedFalse(contentId);
        return commentMapper.toResponseList(comments);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByParentId(String parentId) {
        List<Comment> comments = commentRepository.findByParentCommentId(parentId);
        return commentMapper.toResponseList(comments);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CommentResponse> getRootCommentsByContentId(String contentId, Pageable pageable) {
        return commentRepository
                .findByContent_IdAndParentCommentIsNullAndIsDeletedFalse(contentId, pageable)
                .map(commentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CommentResponse> getRepliesByParentId(String parentId, Pageable pageable) {
        return commentRepository
                .findByParentComment_IdAndIsDeletedFalse(parentId, pageable)
                .map(commentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByContentId(String contentId) {
        return commentRepository.countByContent_IdAndIsDeletedFalse(contentId);
    }
}

