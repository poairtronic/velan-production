package com.velan.production.shared.audit;

import com.velan.production.shared.error.CorrelationIdFilter;
import com.velan.production.shared.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

/**
 * Append-only audit logging service writing to audit_logs.
 */
@Service
public class AuditService {

    public static final UUID SYSTEM_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final AuditProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock utcClock;

    public AuditService(
            AuditLogRepository repository,
            AuditProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<Clock> utcClockProvider) {
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.utcClock = utcClockProvider.getIfAvailable(Clock::systemUTC);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditEvent event) {
        doInsert(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(AuditEvent event) {
        try {
            doInsert(event);
        } catch (Exception ex) {
            log.error("Independent audit insert failed for action={} entityType={} entityId={}",
                    event.action(), event.entityType(), event.entityId(), ex);
        }
    }

    private void doInsert(AuditEvent event) {
        ActorInfo actor = resolveActor();
        Map<String, Object> details = new LinkedHashMap<>();
        if (event.oldValue() != null) {
            details.put("oldValue", AuditRedactor.redact(event.oldValue(), properties.redactKeys()));
        }
        if (event.newValue() != null) {
            details.put("newValue", AuditRedactor.redact(event.newValue(), properties.redactKeys()));
        }
        if (event.reason() != null && !event.reason().isBlank()) {
            details.put("reason", event.reason());
        }
        String ip = resolveClientIp();
        if (ip != null) {
            details.put("ipAddress", ip);
        }
        String detailsJson = details.isEmpty() ? null : objectMapper.writeValueAsString(details);
        repository.insert(
                UUID.randomUUID(),
                event.scId(),
                MDC.get(CorrelationIdFilter.MDC_KEY),
                actor.actorId(),
                actor.actorRole(),
                event.action().name(),
                event.entityType(),
                event.entityId(),
                detailsJson,
                Instant.now(utcClock));
    }

    private static ActorInfo resolveActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            String role = user.roles().isEmpty() ? null : user.roles().getFirst();
            return new ActorInfo(user.userId(), role);
        }
        return new ActorInfo(SYSTEM_ACTOR_ID, "SYSTEM");
    }

    private static String resolveClientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            String forwarded = req.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return req.getRemoteAddr();
        }
        return null;
    }

    private record ActorInfo(UUID actorId, String actorRole) {
    }
}
