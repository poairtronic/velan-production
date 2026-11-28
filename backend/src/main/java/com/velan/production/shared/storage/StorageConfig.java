package com.velan.production.shared.storage;

import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    @Bean
    public FileStoragePort fileStoragePort(StorageProperties properties) {
        return switch (properties.type().trim().toLowerCase()) {
            case "local" -> new LocalDiskFileStorage(Path.of(properties.local().root()));
            case "memory" -> new InMemoryFileStorage();
            case "disabled" -> new DisabledFileStorage();
            case "r2" -> throw new IllegalStateException("R2 adapter is not implemented yet");
            default -> throw new IllegalStateException("Unsupported app.storage.type: " + properties.type());
        };
    }
}
