package com.velan.production.shared.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing with UTC timestamps and current-user resolution
 * whenever a datasource URL is configured.
 * Security principal wiring will be supplied in Part 8.5.
 */
@Configuration
@ConditionalOnExpression("'${spring.autoconfigure.exclude[0]:}' == ''")
@EnableJpaAuditing(dateTimeProviderRef = "utcDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }

    @Bean
    public DateTimeProvider utcDateTimeProvider(Clock utcClock) {
        return () -> Optional.of(Instant.now(utcClock));
    }

    @Bean
    public AuditorAware<UUID> auditorAware() {
        return Optional::empty;
    }
}
