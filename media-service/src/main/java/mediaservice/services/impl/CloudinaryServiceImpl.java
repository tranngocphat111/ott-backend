package mediaservice.services.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mediaservice.services.CloudinaryService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryServiceImpl implements CloudinaryService {

    private final Cloudinary cloudinary;

    @Override
    public Map<String, Object> uploadFile(MultipartFile file, String folder) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/") && !contentType.startsWith("video/"))) {
            throw new IllegalArgumentException("Only image and video files are allowed");
        }

        log.info("Uploading file: {} to folder: {}", file.getOriginalFilename(), folder);

        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String filename = UUID.randomUUID().toString();

        if (originalFilename != null && originalFilename.contains(".")) {
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            filename = filename + extension;
        }

        // Upload to Cloudinary
        Map<String, Object> uploadParams = ObjectUtils.asMap(
                "folder", folder,
                "public_id", filename,
                "resource_type", "auto"
        );

        Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), uploadParams);

        log.info("File uploaded successfully. Public ID: {}, URL: {}",
                uploadResult.get("public_id"), uploadResult.get("secure_url"));

        return uploadResult;
    }

    @Override
    public Map<String, Object> uploadFromUrl(String imageUrl, String folder) throws IOException {
        if (imageUrl == null || imageUrl.isEmpty()) {
            throw new IllegalArgumentException("Image URL is required");
        }

        log.info("========== CLOUDINARY UPLOAD START ==========");
        log.info("Source URL: {}", imageUrl);
        log.info("Target folder: {}", folder);

        // Generate unique filename
        String filename = UUID.randomUUID().toString();
        log.debug("Generated filename: {}", filename);

        // Upload to Cloudinary from URL
        Map<String, Object> uploadParams = ObjectUtils.asMap(
                "folder", folder,
                "public_id", filename,
                "resource_type", "auto"
        );

        log.debug("Upload params: {}", uploadParams);
        log.info("Calling Cloudinary API...");

        Map<String, Object> uploadResult = cloudinary.uploader().upload(imageUrl, uploadParams);

        log.info("✅ Upload SUCCESS!");
        log.info("Public ID: {}", uploadResult.get("public_id"));
        log.info("Secure URL: {}", uploadResult.get("secure_url"));
        log.info("Width: {}, Height: {}", uploadResult.get("width"), uploadResult.get("height"));
        log.info("Format: {}", uploadResult.get("format"));
        log.info("========== CLOUDINARY UPLOAD END ==========");

        return uploadResult;
    }

    @Override
    public Map<String, Object> uploadFile(MultipartFile file) throws IOException {
        return uploadFile(file, "media");
    }

    @Override
    public void deleteFile(String publicId) throws IOException {
        if (publicId == null || publicId.isEmpty()) {
            throw new IllegalArgumentException("Public ID is required");
        }

        log.info("Deleting file with public ID: {}", publicId);

        Map<String, Object> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());

        log.info("File deleted. Result: {}", result.get("result"));
    }

    @Override
    public String getFileUrl(String publicId) {
        if (publicId == null || publicId.isEmpty()) {
            return null;
        }

        return cloudinary.url().secure(true).generate(publicId);
    }
}

