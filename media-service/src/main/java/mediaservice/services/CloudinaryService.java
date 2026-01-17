package mediaservice.services;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

public interface CloudinaryService {
    Map<String, Object> uploadFile(MultipartFile file, String folder) throws IOException;
    Map<String, Object> uploadFile(MultipartFile file) throws IOException;
    Map<String, Object> uploadFromUrl(String imageUrl, String folder) throws IOException;
    void deleteFile(String publicId) throws IOException;
    String getFileUrl(String publicId);
}

