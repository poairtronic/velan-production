package com.velan.production.shared.storage;

import com.velan.production.shared.error.NotFoundException;
import com.velan.production.shared.error.ValidationException;
import com.velan.production.shared.security.AuthenticatedUser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class FileService {

    public static final UUID SYSTEM_UPLOADER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private static final Map<String, Set<String>> EXT_TO_CONTENT_TYPES = Map.of(
            "pdf", Set.of("application/pdf"),
            "png", Set.of("image/png"),
            "jpg", Set.of("image/jpeg"),
            "jpeg", Set.of("image/jpeg"),
            "xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            "zip", Set.of("application/zip", "application/x-zip-compressed")
    );

    private final FileStoragePort storage;
    private final FileMetadataRepository repository;

    public FileService(FileStoragePort storage, FileMetadataRepository repository) {
        this.storage = storage;
        this.repository = repository;
    }

    @Transactional
    public FileMetadataRepository.FileRecord upload(
            FileCategory category,
            UUID scId,
            String originalFilename,
            String contentType,
            InputStream input) {

        if (category == null) {
            throw new ValidationException("File category is required");
        }
        String sanitizedName = sanitizeFilename(originalFilename);
        String ext = extractLastExtension(sanitizedName);
        if (!category.allowedExtensions().contains(ext)) {
            throw new ValidationException("File extension not allowed for category " + category);
        }
        if (contentType == null || !EXT_TO_CONTENT_TYPES.getOrDefault(ext, Set.of()).contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new ValidationException("Content type does not match extension " + ext);
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("velan-upload-", ".tmp");
            UploadResult result = streamAndValidate(input, tempFile, category.maxBytes(), ext);

            UUID fileId = UUID.randomUUID();
            String prefix = scId != null ? "sc/" + scId : "common";
            String key = prefix + "/" + category.name().toLowerCase(Locale.ROOT) + "/" + fileId;

            try (InputStream in = Files.newInputStream(tempFile)) {
                storage.put(key, in, result.sizeBytes(), contentType);
            }

            FileMetadataRepository.FileRecord record = new FileMetadataRepository.FileRecord(
                    fileId,
                    scId,
                    key,
                    sanitizedName,
                    contentType,
                    result.sizeBytes(),
                    result.sha256(),
                    resolveUploader(),
                    Instant.now());

            try {
                repository.insert(record);
            } catch (RuntimeException ex) {
                storage.delete(key);
                throw ex;
            }
            return record;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to buffer upload", ex);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public DownloadResult download(UUID fileId) {
        FileMetadataRepository.FileRecord record = repository.findById(fileId)
                .orElseThrow(() -> new NotFoundException("File not found"));
        return new DownloadResult(record, storage.get(record.storageKey()));
    }

    @Transactional
    public void delete(UUID fileId) {
        FileMetadataRepository.FileRecord record = repository.findById(fileId)
                .orElseThrow(() -> new NotFoundException("File not found"));
        repository.deleteById(fileId);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storage.delete(record.storageKey());
                }
            });
        } else {
            storage.delete(record.storageKey());
        }
    }

    private static UploadResult streamAndValidate(InputStream input, Path tempFile, long maxBytes, String ext)
            throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }

        byte[] header = new byte[8];
        int headerRead = 0;
        long total = 0;
        byte[] buf = new byte[8192];

        try (OutputStream out = Files.newOutputStream(tempFile)) {
            int n;
            while ((n = input.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new ValidationException("File exceeds maximum allowed size of " + maxBytes + " bytes");
                }
                if (headerRead < header.length) {
                    int toCopy = Math.min(n, header.length - headerRead);
                    System.arraycopy(buf, 0, header, headerRead, toCopy);
                    headerRead += toCopy;
                }
                digest.update(buf, 0, n);
                out.write(buf, 0, n);
            }
        }

        if (!matchesSignature(ext, header, headerRead)) {
            throw new ValidationException("File content signature does not match extension " + ext);
        }

        return new UploadResult(total, HexFormat.of().formatHex(digest.digest()));
    }

    static boolean matchesSignature(String ext, byte[] h, int len) {
        return switch (ext) {
            case "pdf" -> len >= 5 && h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F' && h[4] == '-';
            case "png" -> len >= 4 && (h[0] & 0xFF) == 0x89 && h[1] == 0x50 && h[2] == 0x4E && h[3] == 0x47;
            case "jpg", "jpeg" -> len >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            case "zip", "xlsx" -> len >= 4 && h[0] == 0x50 && h[1] == 0x4B && h[2] == 0x03 && h[3] == 0x04;
            default -> false;
        };
    }

    static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Filename must not be blank");
        }
        if (name.indexOf('\0') >= 0) {
            throw new ValidationException("Filename contains null byte");
        }
        String cleaned = name.replace('\\', '/');
        int lastSlash = cleaned.lastIndexOf('/');
        if (lastSlash >= 0) {
            cleaned = cleaned.substring(lastSlash + 1);
        }
        cleaned = cleaned.replaceAll("[\\p{Cntrl}]", "").trim();
        if (cleaned.isEmpty()) {
            throw new ValidationException("Filename is invalid");
        }
        return cleaned;
    }

    static String extractLastExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new ValidationException("File must have an extension");
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static UUID resolveUploader() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user.userId();
        }
        return SYSTEM_UPLOADER_ID;
    }

    private record UploadResult(long sizeBytes, String sha256) {
    }

    public record DownloadResult(FileMetadataRepository.FileRecord metadata, InputStream stream) {
    }
}
