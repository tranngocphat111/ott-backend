package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.dtos.requests.PostRequest;
import mediaservice.dtos.responses.PostResponse;
import mediaservice.mappers.PostMapper;
import mediaservice.models.Post;
import mediaservice.models.User;
import mediaservice.models.enums.VisibilityType;
import mediaservice.repositories.PostRepository;
import mediaservice.repositories.UserRepository;
import mediaservice.services.CloudinaryService;
import mediaservice.services.PostService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final PostMapper postMapper;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;

    @Override
    @Transactional(readOnly = true)
    public List<PostResponse> getAllPosts() {
        log.info("Fetching all posts");
        List<Post> posts = postRepository.findAll();
        log.info("Found {} posts", posts.size());

        return posts.stream()
                .map(postMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public List<PostResponse> getAllPostsByUserId(String userId) {
        log.info("Fetching all posts by user id: " + userId);

        User user = new User();
        user.setId(userId);
        List<Post> posts = postRepository.findByUser(user);
        log.info("Found {} posts", posts.size());

        return posts.stream()
                .map(postMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse getPostById(String id) {
        log.info("Fetching post with id: {}", id);
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));

        return postMapper.toResponse(post);
    }

    @Override
    @Transactional
    public PostResponse createPost(PostRequest request) {
        log.info("Creating new post for user: {}", request.getUserId());
        log.debug("Original request metadata: {}", request.getMetadata());

        Post post = postMapper.toEntity(request);

        // Process and upload images to Cloudinary
        Map<String, Object> processedMetadata = processAndUploadImages(
                request.getMetadata(),
                request.getUserId()
        );

        if (processedMetadata != null) {
            post.setMetadata(processedMetadata);
        }

        Post savedPost = postRepository.save(post);
        log.info("Post created with id: {}", savedPost.getId());

        User user = userRepository.findById(savedPost.getUser().getId()).orElse(null);
        savedPost.setUser(user);

        return postMapper.toResponse(savedPost);
    }

    @Override
    @Transactional
    public PostResponse updatePost(String id, PostRequest request) {
        log.info("Updating post with id: {}", id);

        Post existingPost = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));

        // Update basic fields
        if (request.getContent() != null) {
            existingPost.setContent(request.getContent());
        }

        if (request.getVisibility() != null) {
            existingPost.setVisibility(
                    VisibilityType.valueOf(request.getVisibility().toUpperCase())
            );
        }

        // Process and upload images to Cloudinary
        if (request.getMetadata() != null) {
            Map<String, Object> processedMetadata = processAndUploadImages(
                    request.getMetadata(),
                    request.getUserId()
            );

            if (processedMetadata != null) {
                existingPost.setMetadata(processedMetadata);
            }
        }

        Post updatedPost = postRepository.save(existingPost);
        log.info("Post updated successfully: {}", id);

        return postMapper.toResponse(updatedPost);
    }

    @Override
    @Transactional
    public void deletePost(String id) {
        log.info("Deleting post with id: {}", id);

        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));

        postRepository.delete(post);
        log.info("Post deleted successfully: {}", id);
    }

    /**
     * Process images in metadata and upload external URLs to Cloudinary
     *
     * @param metadata Original metadata containing images array
     * @param userId   User ID for organizing Cloudinary folder structure
     * @return Updated metadata with Cloudinary URLs, or original if no processing needed
     */
    private Map<String, Object> processAndUploadImages(Map<String, Object> metadata, String userId) {
        // Check if metadata has images array
        if (metadata == null || metadata.get("images") == null) {
            log.info("No images to process in metadata");
            return metadata;
        }

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> images = (List<Map<String, String>>) metadata.get("images");
            log.info("Found {} images to process", images.size());

            List<Map<String, String>> processedImages = new ArrayList<>();
            String cloudinaryFolder = String.format("media/posts/%s", userId);

            for (int i = 0; i < images.size(); i++) {
                Map<String, String> image = images.get(i);
                String imageUrl = image.get("imageUrl");
                String caption = image.get("caption");

                log.info("Processing image {}/{}: URL={}", i + 1, images.size(), imageUrl);

                if (imageUrl != null && !imageUrl.isEmpty()) {
                    // Check if URL is already from Cloudinary
                    if (imageUrl.contains("res.cloudinary.com")) {
                        log.info("Image already on Cloudinary, keeping as is");
                        processedImages.add(image);
                    } else {
                        // Upload external URL to Cloudinary
                        Map<String, String> processedImage = uploadImageToCloudinary(
                                imageUrl,
                                caption,
                                cloudinaryFolder
                        );
                        processedImages.add(processedImage);
                    }
                }
            }

            log.info("Finished processing {} images. Updating metadata...", processedImages.size());

            // Create updated metadata
            Map<String, Object> updatedMetadata = new java.util.HashMap<>(metadata);
            updatedMetadata.put("images", processedImages);

            log.debug("Updated metadata: {}", updatedMetadata);
            return updatedMetadata;

        } catch (Exception e) {
            log.error("Error processing images: {}", e.getMessage(), e);
            // Return original metadata if processing fails
            return metadata;
        }
    }

    /**
     * Upload a single image from URL to Cloudinary
     *
     * @param imageUrl        Source image URL
     * @param caption          Image caption
     * @param cloudinaryFolder Target folder in Cloudinary
     * @return Processed image data with Cloudinary URL
     */
    private Map<String, String> uploadImageToCloudinary(String imageUrl, String caption, String cloudinaryFolder) {
        log.info("Uploading external image to Cloudinary...");

        try {
            log.debug("Target folder: {}", cloudinaryFolder);

            Map<String, Object> uploadResult = cloudinaryService.uploadFromUrl(imageUrl, cloudinaryFolder);

            String newImageUrl = (String) uploadResult.get("secure_url");
            log.info("✅ Upload SUCCESS! New URL: {}", newImageUrl);

            Map<String, String> processedImage = new java.util.HashMap<>();
            processedImage.put("imageUrl", newImageUrl);
            processedImage.put("caption", caption != null ? caption : "");
            processedImage.put("publicId", (String) uploadResult.get("public_id"));
            processedImage.put("width", String.valueOf(uploadResult.get("width")));
            processedImage.put("height", String.valueOf(uploadResult.get("height")));
            processedImage.put("format", (String) uploadResult.get("format"));
            processedImage.put("resourceType", (String) uploadResult.get("resource_type"));

            log.info("Processed image added to list");
            return processedImage;

        } catch (Exception e) {
            log.error("❌ Upload FAILED for image: {}", imageUrl);
            log.error("Error details: {}", e.getMessage(), e);

            // Return original image data if upload fails
            Map<String, String> originalImage = new java.util.HashMap<>();
            originalImage.put("imageUrl", imageUrl);
            originalImage.put("caption", caption != null ? caption : "");

            log.warn("Kept original URL due to upload failure");
            return originalImage;
        }
    }
}
