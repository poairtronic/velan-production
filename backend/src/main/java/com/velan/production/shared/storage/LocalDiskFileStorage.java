package com.velan.production.shared.storage;

import com.velan.production.shared.error.NotFoundException;
import com.velan.production.shared.error.ValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class LocalDiskFileStorage implements FileStoragePort {

    private final Path root;

    public LocalDiskFileStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, InputStream data, long contentLength, String contentType) {
        Path target = resolveSafe(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write file to local storage", ex);
        }
    }

    @Override
    public InputStream get(String key) {
        Path target = resolveSafe(key);
        if (!Files.exists(target)) {
            throw new NotFoundException("File not found");
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read file from local storage", ex);
        }
    }

    @Override
    public void delete(String key) {
        Path target = resolveSafe(key);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete file from local storage", ex);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolveSafe(key));
    }

    private Path resolveSafe(String key) {
        if (key == null || key.isBlank()) {
            throw new ValidationException("Storage key must not be blank");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new ValidationException("Invalid storage key path");
        }
        return resolved;
    }
}
