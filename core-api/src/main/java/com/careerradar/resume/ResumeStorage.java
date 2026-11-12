package com.careerradar.resume;

import com.careerradar.common.error.BadRequestException;
import com.careerradar.common.error.NotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Stores PDFs under a configured local directory. The filename is derived from the resume
 * UUID, so no user-controlled path ever reaches the filesystem.
 */
@Service
public class ResumeStorage {

    private static final Logger log = LoggerFactory.getLogger(ResumeStorage.class);

    private final Path root;
    private final long maxBytes;

    public ResumeStorage(ResumeStorageProperties properties) {
        this.root = Path.of(properties.getDir()).toAbsolutePath().normalize();
        this.maxBytes = properties.getMaxBytes();
    }

    public String store(UUID publicId, byte[] bytes) {
        if (bytes.length == 0) {
            throw new BadRequestException("PDF file is empty");
        }
        if (bytes.length > maxBytes) {
            throw new BadRequestException("PDF file exceeds " + maxBytes + " bytes");
        }
        String filename = publicId + ".pdf";
        try {
            Files.createDirectories(root);
            Files.write(
                    root.resolve(filename),
                    bytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            return filename;
        } catch (IOException ex) {
            log.error("Failed to store resume PDF {}: {}", filename, ex.getClass().getSimpleName());
            throw new IllegalStateException("Could not store resume PDF", ex);
        }
    }

    public Resource load(String filename) {
        Path path = resolve(filename);
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("Resume PDF not found");
        }
        return new FileSystemResource(path);
    }

    public void delete(String filename) {
        if (filename == null || filename.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(filename));
        } catch (IOException ex) {
            log.warn("Failed to delete resume PDF {}: {}", filename, ex.getClass().getSimpleName());
        }
    }

    private Path resolve(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new NotFoundException("Resume PDF not found");
        }
        Path path = root.resolve(filename).normalize();
        if (!path.startsWith(root)) {
            throw new NotFoundException("Resume PDF not found");
        }
        return path;
    }
}
