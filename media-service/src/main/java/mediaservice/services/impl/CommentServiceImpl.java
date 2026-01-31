package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.CommentRequest;
import mediaservice.dtos.responses.CommentResponse;
import mediaservice.mappers.CommentMapper;
import mediaservice.models.Comment;
import mediaservice.repositories.CommentRepository;
import mediaservice.services.CommentService;
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

    @Override
    @Transactional
    public CommentResponse createComment(CommentRequest request) {
        Comment comment = commentMapper.toEntity(request);
        Comment savedComment = commentRepository.save(comment);
        return commentMapper.toResponse(savedComment);
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
    public CommentResponse updateComment(String id, CommentRequest request) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Comment not found with id: " + id));
        commentMapper.updateEntity(request, comment);
        Comment updatedComment = commentRepository.save(comment);
        return commentMapper.toResponse(updatedComment);
    }

    @Override
    @Transactional
    public void deleteComment(String id) {
        if (!commentRepository.existsById(id)) {
            throw new RuntimeException("Comment not found with id: " + id);
        }
        commentRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByContentId(String contentId) {
        List<Comment> comments = commentRepository.findAll(); // TODO: Add custom query
        return commentMapper.toResponseList(comments);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByParentId(String parentId) {
        List<Comment> comments = commentRepository.findAll(); // TODO: Add custom query
        return commentMapper.toResponseList(comments);
    }
}

