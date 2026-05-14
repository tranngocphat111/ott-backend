package mediaservice.utils;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MediaTempFileStore {

    private MediaTempFileStore() {
    }

    public static Path saveToTemp(MultipartFile file, String prefix) throws IOException {
        String originalName = file.getOriginalFilename();
        String suffix = "";
        if (originalName != null && originalName.contains(".")) {
            suffix = originalName.substring(originalName.lastIndexOf('.'));
        }

        Path tempFile = Files.createTempFile(prefix, suffix);
        file.transferTo(tempFile);
        return tempFile;
    }
}
