package com.renterp.domain.file.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Development storage: files under {@code app.files.local-dir}
 * (FILES_DIR, default {@code ./uploads} next to where the backend runs).
 *
 * Every key is resolved against the root and checked to stay inside it, so a
 * key can never point outside the upload folder even if one were malformed.
 */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(@Value("${app.files.local-dir:./uploads}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    @Override
    public void store(String key, InputStream content, long size, String contentType) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
        try {
            Files.copy(content, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root) || p.equals(root)) {
            throw new IllegalArgumentException("Storage key escapes the upload folder");
        }
        return p;
    }
}
