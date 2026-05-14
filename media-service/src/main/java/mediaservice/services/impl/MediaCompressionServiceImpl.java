package mediaservice.services.impl;

import lombok.extern.slf4j.Slf4j;
import mediaservice.services.MediaCompressionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class MediaCompressionServiceImpl implements MediaCompressionService {

    @Value("${media.ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    @Override
    public Path compressVideo(Path inputPath) {
        Path outputPath = buildOutputPath(inputPath, "-compressed", ".mp4");

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-i");
        command.add(inputPath.toString());
        command.add("-vf");
        command.add("scale=w='min(1280,iw)':h='min(720,ih)':force_original_aspect_ratio=decrease");
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("veryfast");
        command.add("-profile:v");
        command.add("high");
        command.add("-level");
        command.add("4.0");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-b:v");
        command.add("1200k");
        command.add("-maxrate");
        command.add("1500k");
        command.add("-bufsize");
        command.add("2000k");
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("128k");
        command.add("-movflags");
        command.add("+faststart");
        command.add(outputPath.toString());

        runCommand(command, "video", inputPath, outputPath);
        return outputPath;
    }

    @Override
    public Path compressAudio(Path inputPath) {
        Path outputPath = buildOutputPath(inputPath, "-compressed", ".m4a");

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-i");
        command.add(inputPath.toString());
        command.add("-vn");
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("128k");
        command.add("-ar");
        command.add("44100");
        command.add(outputPath.toString());

        runCommand(command, "audio", inputPath, outputPath);
        return outputPath;
    }

    private Path buildOutputPath(Path inputPath, String suffix, String extension) {
        Path parent = inputPath.getParent();
        String baseName = stripExtension(inputPath.getFileName().toString());
        String fileName = baseName + suffix + extension;
        return parent != null ? parent.resolve(fileName) : Path.of(fileName);
    }

    private String stripExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }

    private void runCommand(List<String> command, String mediaType, Path inputPath, Path outputPath) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            String output = readProcessOutput(process);
            int exitCode = process.waitFor();

            if (exitCode != 0 || !Files.exists(outputPath)) {
                throw new IllegalStateException(
                    "FFmpeg failed for " + mediaType + " (exit code " + exitCode + ")\n" + output
                );
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("FFmpeg interrupted for {}: {}", mediaType, inputPath, ex);
            throw new RuntimeException("Failed to compress " + mediaType, ex);
        } catch (IOException ex) {
            log.error("FFmpeg error for {}: {}", mediaType, inputPath, ex);
            throw new RuntimeException("Failed to compress " + mediaType, ex);
        }
    }

    private String readProcessOutput(Process process) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }
}
