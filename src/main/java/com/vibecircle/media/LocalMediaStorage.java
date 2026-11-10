package com.vibecircle.media;

import com.vibecircle.common.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LocalMediaStorage implements MediaStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalMediaStorage.class);
    private final Path root;

    public LocalMediaStorage(@Value("${vibecircle.media.dir:./uploads}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create media directory " + root, e);
        }
    }

    @Override
    public String store(InputStream in, String extension) {
        String key = UUID.randomUUID() + "." + extension;
        try {
            Files.copy(in, resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store media", e);
        }
        return key;
    }

    @Override
    public Resource load(String key) {
        Path path = resolve(key);
        if (!Files.isReadable(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Media not found");
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Could not delete media file {}", key, e);
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid media key");
        }
        return path;
    }
}
