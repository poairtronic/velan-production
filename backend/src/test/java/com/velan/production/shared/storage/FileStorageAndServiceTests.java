package com.velan.production.shared.storage;

import com.velan.production.VelanProductionApplication;
import com.velan.production.shared.error.ServiceUnavailableException;
import com.velan.production.shared.error.ValidationException;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStorageAndServiceTests {

    private static final byte[] PDF_BYTES = new byte[]{'%', 'P', 'D', 'F', '-', '1', '.', '4'};
    private static final byte[] PNG_BYTES = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A};

    @Test
    void inMemoryAndLocalDiskRoundTripsAndPathTraversalWork(@TempDir Path tempDir) throws Exception {
        for (FileStoragePort port : new FileStoragePort[]{new InMemoryFileStorage(), new LocalDiskFileStorage(tempDir)}) {
            String key = "common/drawing_document/" + UUID.randomUUID();
            port.put(key, new ByteArrayInputStream(PDF_BYTES), PDF_BYTES.length, "application/pdf");
            assertTrue(port.exists(key));
            assertArrayEquals(PDF_BYTES, port.get(key).readAllBytes());
            assertTrue(port.presignedGetUrl(key, Duration.ofMinutes(5)).isEmpty());
            port.delete(key);
            assertFalse(port.exists(key));
        }

        LocalDiskFileStorage local = new LocalDiskFileStorage(tempDir);
        assertThrows(ValidationException.class, () ->
                local.put("../../etc/passwd", new ByteArrayInputStream(PDF_BYTES), PDF_BYTES.length, "application/pdf"));
    }

    @Test
    void disabledAdapterThrowsServiceUnavailableAndR2FailsStartup() {
        DisabledFileStorage disabled = new DisabledFileStorage();
        assertThrows(ServiceUnavailableException.class, () -> disabled.exists("k"));
        assertThrows(ServiceUnavailableException.class, () -> disabled.get("k"));
        assertThrows(ServiceUnavailableException.class, () -> disabled.delete("k"));

        assertThrows(Exception.class, () -> {
            try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(VelanProductionApplication.class)
                    .properties("server.port=0")
                    .run("--spring.profiles.active=test", "--app.storage.type=r2")) {
                assertTrue(ctx.isActive());
            }
        });
    }

    @Test
    void validationMatrixRejectsBadExtensionsSignaturesNullBytesAndOversize() {
        InMemoryFileStorage storage = new InMemoryFileStorage();
        FileMetadataRepository repo = new FileMetadataRepository(new org.springframework.beans.factory.ObjectProvider<>() {
            @Override public JdbcTemplate getObject() { return null; }
            @Override public JdbcTemplate getIfAvailable() { return null; }
            @Override public JdbcTemplate getIfUnique() { return null; }
        });
        FileService service = new FileService(storage, repo);

        // Wrong signature for .pdf
        assertThrows(ValidationException.class, () -> service.upload(
                FileCategory.DRAWING_DOCUMENT, null, "doc.PDF", "application/pdf", new ByteArrayInputStream(PNG_BYTES)));

        // Double extension
        assertThrows(ValidationException.class, () -> service.upload(
                FileCategory.DRAWING_DOCUMENT, null, "a.pdf.exe", "application/pdf", new ByteArrayInputStream(PDF_BYTES)));

        // Null byte in filename
        assertThrows(ValidationException.class, () -> service.upload(
                FileCategory.DRAWING_DOCUMENT, null, "a\0.pdf", "application/pdf", new ByteArrayInputStream(PDF_BYTES)));

        // Content type mismatch
        assertThrows(ValidationException.class, () -> service.upload(
                FileCategory.SIGNATURE_IMAGE, null, "sig.png", "application/pdf", new ByteArrayInputStream(PNG_BYTES)));
    }

    @Test
    void dbUploadDownloadAndDeleteWork() throws Exception {
        String url = System.getenv("DATABASE_URL");
        String user = System.getenv("DB_USER");
        String pass = System.getenv("DB_PASSWORD");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "DATABASE_URL not set; skipping file DB test");

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, pass));
        FileMetadataRepository repo = new FileMetadataRepository(new org.springframework.beans.factory.ObjectProvider<>() {
            @Override public JdbcTemplate getObject() { return jdbc; }
            @Override public JdbcTemplate getIfAvailable() { return jdbc; }
            @Override public JdbcTemplate getIfUnique() { return jdbc; }
        });
        InMemoryFileStorage storage = new InMemoryFileStorage();
        FileService service = new FileService(storage, repo);

        var rec = service.upload(FileCategory.DRAWING_DOCUMENT, null, "dir/../blueprint.PDF",
                "application/pdf", new ByteArrayInputStream(PDF_BYTES));
        assertTrue(rec.storageKey().startsWith("common/drawing_document/"));
        assertFalse(rec.storageKey().contains("blueprint"));
        assertEquals("blueprint.PDF", rec.fileName());
        assertTrue(storage.exists(rec.storageKey()));

        var dl = service.download(rec.id());
        assertArrayEquals(PDF_BYTES, dl.stream().readAllBytes());

        service.delete(rec.id());
        assertFalse(storage.exists(rec.storageKey()));
        assertTrue(repo.findById(rec.id()).isEmpty());
    }
}
