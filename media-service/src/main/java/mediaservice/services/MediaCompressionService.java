package mediaservice.services;

import java.nio.file.Path;

public interface MediaCompressionService {

    Path compressVideo(Path inputPath);

    Path compressAudio(Path inputPath);
}
