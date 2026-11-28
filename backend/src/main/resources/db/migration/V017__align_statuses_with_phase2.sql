-- Phase 7.9: Align status/decision/disposition constraints with Phase 2 state machines and add SC cancellation metadata

-- 1. sc_headers: stored lifecycle states (DRAFT_INTAKE, ACTIVE, CANCELLED, ARCHIVED) + cancellation fields
ALTER TABLE sc_headers
    ALTER COLUMN status TYPE VARCHAR(24);

ALTER TABLE sc_headers
    DROP CONSTRAINT chk_sc_headers_status,
    ADD CONSTRAINT chk_sc_headers_status
        CHECK (status IN ('DRAFT_INTAKE', 'ACTIVE', 'CANCELLED', 'ARCHIVED'));

ALTER TABLE sc_headers
    ADD COLUMN cancel_reason TEXT NULL,
    ADD COLUMN cancelled_by  UUID NULL,
    ADD COLUMN cancelled_at  TIMESTAMPTZ NULL;

ALTER TABLE sc_headers
    ADD CONSTRAINT fk_sc_headers_cancelled_by
        FOREIGN KEY (cancelled_by) REFERENCES users(id) ON DELETE RESTRICT;

CREATE INDEX idx_sc_headers_cancelled_by
    ON sc_headers (cancelled_by)
    WHERE cancelled_by IS NOT NULL;

ALTER TABLE sc_headers
    ADD CONSTRAINT chk_sc_headers_cancelled
        CHECK (
            status <> 'CANCELLED'
            OR (
                cancel_reason IS NOT NULL
                AND char_length(trim(cancel_reason)) > 10
                AND cancelled_by IS NOT NULL
                AND cancelled_at IS NOT NULL
            )
        );

-- 2. pieces & piece_events: widen status columns for IN_PRODUCTION_EXTRA_PROCESS (27 chars) and enforce Phase 2.3 states
ALTER TABLE pieces
    ALTER COLUMN status TYPE VARCHAR(32);

ALTER TABLE pieces
    ADD CONSTRAINT chk_pieces_status
        CHECK (status IN (
            'CREATED',
            'AWAITING_MATERIAL',
            'IN_PRODUCTION',
            'ON_HOLD_QC_FAILED',
            'IN_PRODUCTION_REWORK',
            'IN_PRODUCTION_EXTRA_PROCESS',
            'COMPLETED',
            'SCRAPPED'
        ));

ALTER TABLE piece_events
    ALTER COLUMN from_status TYPE VARCHAR(32),
    ALTER COLUMN to_status TYPE VARCHAR(32);

-- 3. piece_operations: enforce Phase 2.4 states
ALTER TABLE piece_operations
    ADD CONSTRAINT chk_piece_operations_status
        CHECK (status IN (
            'PENDING',
            'READY',
            'OPENED',
            'QC_PENDING',
            'QC_IN_PROGRESS',
            'PASSED',
            'FAILED',
            'CONCESSION_ACCEPTED',
            'BYPASSED',
            'CANCELLED'
        ));

-- 4. qc_inspections: enforce Phase 2.5 §3.2 states/verdicts
ALTER TABLE qc_inspections
    ADD CONSTRAINT chk_qc_inspections_decision
        CHECK (decision IN (
            'SCHEDULED',
            'CLAIMED_IN_PROGRESS',
            'PASSED',
            'FAILED',
            'CANCELLED'
        ));

-- 5. qc_exceptions: align status with Phase 2.5 §3.3
ALTER TABLE qc_exceptions
    DROP CONSTRAINT chk_qc_exceptions_status,
    ADD CONSTRAINT chk_qc_exceptions_status
        CHECK (status IN (
            'PENDING_PM_DISPOSITION',
            'RESOLVED_CONCESSION',
            'RESOLVED_REWORK',
            'RESOLVED_EXTRA_PROCESS',
            'RESOLVED_SCRAP',
            'CLOSED'
        ));

-- 6. pm_dispositions: widen disposition for RETURN_TO_EARLIER_OPERATION (27 chars) and align with Phase 2.5
ALTER TABLE pm_dispositions
    ALTER COLUMN disposition TYPE VARCHAR(32);

ALTER TABLE pm_dispositions
    DROP CONSTRAINT chk_pm_dispositions_disposition,
    ADD CONSTRAINT chk_pm_dispositions_disposition
        CHECK (disposition IN (
            'CONTINUE',
            'RETURN_TO_EARLIER_OPERATION',
            'EXTRA_PROCESS',
            'REJECT_AND_RESTART'
        ));

-- 7. outbox_events: align status with Phase 2.5 §3.4
ALTER TABLE outbox_events
    DROP CONSTRAINT chk_outbox_events_status,
    ADD CONSTRAINT chk_outbox_events_status
        CHECK (status IN (
            'PENDING',
            'IN_FLIGHT',
            'PUBLISHED',
            'RETRY_SCHEDULED',
            'DEAD_LETTER'
        ));
