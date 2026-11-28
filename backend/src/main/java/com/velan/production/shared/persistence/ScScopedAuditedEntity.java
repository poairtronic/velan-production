package com.velan.production.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;

/**
 * Base mapped superclass for Size-Card-scoped entities carrying an immutable sc_id.
 */
@MappedSuperclass
public abstract class ScScopedAuditedEntity extends AuditedEntity {

    @Column(name = "sc_id", nullable = false, updatable = false)
    private UUID scId;

    public UUID getScId() {
        return scId;
    }

    protected void setScId(UUID scId) {
        this.scId = scId;
    }
}
