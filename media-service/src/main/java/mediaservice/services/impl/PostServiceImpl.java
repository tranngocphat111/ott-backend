package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.PostRequest;
import mediaservice.dtos.responses.PostResponse;
import mediaservice.mappers.PostMapper;
import mediaservice.models.Post;
import mediaservice.repositories.PostRepository;
import mediaservice.services.PostService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final PostMapper postMapper;

    @Override
    @Transactional
    public PostResponse createPost(PostRequest request) {
        Post post = postMapper.toEntity(request);
        Post savedPost = postRepository.save(post);
        return postMapper.toResponse(savedPost);
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse getPostById(String id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));
        return postMapper.toResponse(post);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostResponse> getAllPosts() {
        List<Post> posts = postRepository.findAll();
        return postMapper.toResponseList(posts);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> getAllPosts(Pageable pageable) {
        Page<Post> posts = postRepository.findAll(pageable);
        return posts.map(postMapper::toResponse);
    }

    @Override
    @Transactional
    public PostResponse updatePost(String id, PostRequest request) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));
        postMapper.updateEntity(request, post);
        Post updatedPost = postRepository.save(post);
        return postMapper.toResponse(updatedPost);
    }

    @Override
    @Transactional
    public void deletePost(String id) {
        if (!postRepository.existsById(id)) {
            throw new RuntimeException("Post not found with id: " + id);
        }
        postRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostResponse> getPostsByUserId(String userId) {
        List<Post> posts = postRepository.findAll(); // TODO: Add custom query in repository
        return postMapper.toResponseList(posts);
    }
}
