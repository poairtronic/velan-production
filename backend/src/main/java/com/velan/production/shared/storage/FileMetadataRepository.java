package com.velan.production.shared.storage;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC repository for files table. Only shared.storage may access this class.
 */
@Repository
class FileMetadataRepository {

    private final JdbcTemplate jdbcTemplate;

    FileMetadataRepository(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    void insert(FileRecord rec) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate is not available");
        }
        jdbcTemplate.update("""
                INSERT INTO files (id, sc_id, storage_key, file_name, content_type, size_bytes, checksum_sha256, uploaded_by, uploaded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                rec.id(),
                rec.scId(),
                rec.storageKey(),
                rec.fileName(),
                rec.contentType(),
                rec.sizeBytes(),
                rec.checksumSha256(),
                rec.uploadedBy(),
                Timestamp.from(rec.uploadedAt()));
    }

    Optional<FileRecord> findById(UUID id) {
        if (jdbcTemplate == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT id, sc_id, storage_key, file_name, content_type, size_bytes, checksum_sha256, uploaded_by, uploaded_at
                FROM files WHERE id = ?
                """,
                (rs, rowNum) -> new FileRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("sc_id", UUID.class),
                        rs.getString("storage_key"),
                        rs.getString("file_name"),
                        rs.getString("content_type"),
                        rs.getLong("size_bytes"),
                        rs.getString("checksum_sha256"),
                        rs.getObject("uploaded_by", UUID.class),
                        rs.getTimestamp("uploaded_at").toInstant()
                ),
                id).stream().findFirst();
    }

    void deleteById(UUID id) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate is not available");
        }
        jdbcTemplate.update("DELETE FROM files WHERE id = ?", id);
    }

    public record FileRecord(
            UUID id,
            UUID scId,
            String storageKey,
            String fileName,
            String contentType,
            long sizeBytes,
            String checksumSha256,
            UUID uploadedBy,
            Instant uploadedAt) {
    }
}
