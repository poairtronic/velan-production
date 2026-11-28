package com.velan.production.shared.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.stereotype.Component;

/**
 * Storage health indicator active only when app.storage.type is neither disabled nor memory.
 */
@Component("storage")
@Conditional(StorageHealthIndicator.OnActiveStorageCondition.class)
public class StorageHealthIndicator implements HealthIndicator {

    private final StorageProperties properties;

    public StorageHealthIndicator(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        String type = properties.type().trim().toLowerCase();
        if ("local".equals(type)) {
            Path root = Path.of(properties.local().root());
            boolean writable = Files.isDirectory(root) ? Files.isWritable(root) : Files.isWritable(root.toAbsolutePath().getParent());
            return writable
                    ? Health.up().withDetail("type", type).withDetail("root", root.toString()).build()
                    : Health.down().withDetail("type", type).withDetail("root", root.toString()).build();
        }
        return Health.up().withDetail("type", type).build();
    }

    static class OnActiveStorageCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String type = context.getEnvironment().getProperty("app.storage.type", "disabled").trim().toLowerCase();
            return !"disabled".equals(type) && !"memory".equals(type);
        }
    }
}
