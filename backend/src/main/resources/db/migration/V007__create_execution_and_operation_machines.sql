-- Phase 7.5: Execution tables, operation_machines, scoped_piece_id FK, and operator_values lock trigger

CREATE TABLE machines (
    id         UUID PRIMARY KEY,
    code       VARCHAR(64) NOT NULL UNIQUE,
    name       VARCHAR(150) NOT NULL,
    section_id UUID NULL REFERENCES sections(id) ON DELETE RESTRICT,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_machines_section_id ON machines (section_id) WHERE section_id IS NOT NULL;

CREATE TABLE operation_machines (
    id           UUID PRIMARY KEY,
    operation_id UUID NOT NULL REFERENCES operations(id) ON DELETE RESTRICT,
    machine_id   UUID NOT NULL REFERENCES machines(id) ON DELETE RESTRICT,
    CONSTRAINT uq_operation_machines_pair UNIQUE (operation_id, machine_id)
);

CREATE INDEX idx_operation_machines_operation_id ON operation_machines (operation_id);
CREATE INDEX idx_operation_machines_machine_id ON operation_machines (machine_id);

CREATE TABLE pieces (
    id             UUID PRIMARY KEY,
    sc_id          UUID NOT NULL REFERENCES sc_headers(id) ON DELETE RESTRICT,
    sc_product_id  UUID NOT NULL,
    serial_number  VARCHAR(64) NOT NULL UNIQUE,
    status         VARCHAR(24) NOT NULL,
    is_replacement BOOLEAN NOT NULL DEFAULT FALSE,
    version        BIGINT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_pieces_id_sc UNIQUE (id, sc_id),
    CONSTRAINT fk_pieces_sc_product_sc FOREIGN KEY (sc_product_id, sc_id) REFERENCES sc_products(id, sc_id) ON DELETE RESTRICT
);

CREATE INDEX idx_pieces_sc_status ON pieces (sc_id, status);
CREATE INDEX idx_pieces_sc_product_id ON pieces (sc_product_id);

ALTER TABLE sc_product_template_steps
    ADD CONSTRAINT fk_sc_pts_scoped_piece
    FOREIGN KEY (scoped_piece_id) REFERENCES pieces(id) ON DELETE RESTRICT;

CREATE INDEX idx_sc_pts_scoped_piece_id ON sc_product_template_steps (scoped_piece_id) WHERE scoped_piece_id IS NOT NULL;

CREATE TABLE piece_operations (
    id               UUID PRIMARY KEY,
    sc_id            UUID NOT NULL REFERENCES sc_headers(id) ON DELETE RESTRICT,
    piece_id         UUID NOT NULL,
    snapshot_step_id UUID NOT NULL,
    machine_id       UUID NULL REFERENCES machines(id) ON DELETE RESTRICT,
    attempt_no       INT NOT NULL DEFAULT 1 CHECK (attempt_no >= 1),
    status           VARCHAR(24) NOT NULL,
    started_at       TIMESTAMPTZ NULL,
    completed_at     TIMESTAMPTZ NULL,
    started_by       UUID NULL REFERENCES users(id) ON DELETE RESTRICT,
    completed_by     UUID NULL REFERENCES users(id) ON DELETE RESTRICT,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_piece_ops_id_sc UNIQUE (id, sc_id),
    CONSTRAINT uq_piece_ops_attempt UNIQUE (piece_id, snapshot_step_id, attempt_no),
    CONSTRAINT fk_piece_ops_piece_sc FOREIGN KEY (piece_id, sc_id) REFERENCES pieces(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_piece_ops_step_sc FOREIGN KEY (snapshot_step_id, sc_id) REFERENCES sc_product_template_steps(id, sc_id) ON DELETE RESTRICT
);

CREATE INDEX idx_piece_operations_piece_status ON piece_operations (piece_id, status);
CREATE INDEX idx_piece_operations_sc_id ON piece_operations (sc_id);
CREATE INDEX idx_piece_operations_snapshot_step_id ON piece_operations (snapshot_step_id);
CREATE INDEX idx_piece_operations_machine_id ON piece_operations (machine_id) WHERE machine_id IS NOT NULL;
CREATE INDEX idx_piece_operations_started_by ON piece_operations (started_by) WHERE started_by IS NOT NULL;
CREATE INDEX idx_piece_operations_completed_by ON piece_operations (completed_by) WHERE completed_by IS NOT NULL;

CREATE TABLE piece_events (
    id                 UUID PRIMARY KEY,
    sc_id              UUID NOT NULL REFERENCES sc_headers(id) ON DELETE RESTRICT,
    piece_id           UUID NOT NULL,
    piece_operation_id UUID NULL,
    event_type         VARCHAR(64) NOT NULL,
    from_status        VARCHAR(24) NULL,
    to_status          VARCHAR(24) NULL,
    actor_id           UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    occurred_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    note               TEXT NULL,
    CONSTRAINT fk_piece_events_piece_sc FOREIGN KEY (piece_id, sc_id) REFERENCES pieces(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_piece_events_op_sc FOREIGN KEY (piece_operation_id, sc_id) REFERENCES piece_operations(id, sc_id) ON DELETE RESTRICT
);

CREATE INDEX idx_piece_events_piece_occurred ON piece_events (piece_id, occurred_at);
CREATE INDEX idx_piece_events_sc_id ON piece_events (sc_id);
CREATE INDEX idx_piece_events_op_id ON piece_events (piece_operation_id) WHERE piece_operation_id IS NOT NULL;
CREATE INDEX idx_piece_events_actor_id ON piece_events (actor_id);

CREATE TABLE operator_values (
    id                 UUID PRIMARY KEY,
    sc_id              UUID NOT NULL REFERENCES sc_headers(id) ON DELETE RESTRICT,
    piece_id           UUID NOT NULL,
    piece_operation_id UUID NOT NULL,
    snapshot_step_id   UUID NOT NULL,
    step_parameter_id  UUID NOT NULL,
    status             VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED')),
    val_numeric        NUMERIC(14, 4) NULL,
    val_bool           BOOLEAN NULL,
    val_text           TEXT NULL,
    val_option_code    VARCHAR(64) NULL,
    val_option_label   VARCHAR(150) NULL,
    val_file_id        UUID NULL REFERENCES files(id) ON DELETE RESTRICT,
    judged_min         NUMERIC(14, 4) NULL,
    judged_max         NUMERIC(14, 4) NULL,
    judged_target      NUMERIC(14, 4) NULL,
    result             VARCHAR(16) NULL CHECK (result IS NULL OR result IN ('PASS', 'FAIL', 'NA')),
    submitted_by       UUID NULL REFERENCES users(id) ON DELETE RESTRICT,
    submitted_at       TIMESTAMPTZ NULL,
    CONSTRAINT uq_operator_values_op_param UNIQUE (piece_operation_id, step_parameter_id),
    CONSTRAINT fk_op_vals_piece_sc FOREIGN KEY (piece_id, sc_id) REFERENCES pieces(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_op_vals_op_sc FOREIGN KEY (piece_operation_id, sc_id) REFERENCES piece_operations(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_op_vals_step_sc FOREIGN KEY (snapshot_step_id, sc_id) REFERENCES sc_product_template_steps(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_op_vals_param_sc FOREIGN KEY (step_parameter_id, sc_id) REFERENCES sc_product_step_parameters(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT chk_op_vals_value_count CHECK (
        (CASE WHEN val_numeric IS NOT NULL THEN 1 ELSE 0 END
         + CASE WHEN val_bool IS NOT NULL THEN 1 ELSE 0 END
         + CASE WHEN val_text IS NOT NULL THEN 1 ELSE 0 END
         + CASE WHEN val_option_code IS NOT NULL THEN 1 ELSE 0 END
         + CASE WHEN val_file_id IS NOT NULL THEN 1 ELSE 0 END)
        <= 1
    ),
    CONSTRAINT chk_op_vals_submitted_complete CHECK (
        status <> 'SUBMITTED'
        OR (
            (CASE WHEN val_numeric IS NOT NULL THEN 1 ELSE 0 END
             + CASE WHEN val_bool IS NOT NULL THEN 1 ELSE 0 END
             + CASE WHEN val_text IS NOT NULL THEN 1 ELSE 0 END
             + CASE WHEN val_option_code IS NOT NULL THEN 1 ELSE 0 END
             + CASE WHEN val_file_id IS NOT NULL THEN 1 ELSE 0 END) = 1
            AND result IS NOT NULL
            AND submitted_by IS NOT NULL
            AND submitted_at IS NOT NULL
            AND (val_numeric IS NULL OR judged_target IS NOT NULL)
        )
    )
);

CREATE INDEX idx_operator_values_op_id ON operator_values (piece_operation_id);
CREATE INDEX idx_operator_values_sc_id ON operator_values (sc_id);
CREATE INDEX idx_operator_values_piece_id ON operator_values (piece_id);
CREATE INDEX idx_operator_values_step_id ON operator_values (snapshot_step_id);
CREATE INDEX idx_operator_values_param_id ON operator_values (step_parameter_id);
CREATE INDEX idx_operator_values_file_id ON operator_values (val_file_id) WHERE val_file_id IS NOT NULL;
CREATE INDEX idx_operator_values_submitted_by ON operator_values (submitted_by) WHERE submitted_by IS NOT NULL;

CREATE OR REPLACE FUNCTION trg_operator_values_immutable()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.status = 'SUBMITTED' THEN
        RAISE EXCEPTION 'SUBMITTED operator_values rows are immutable and cannot be updated';
    END IF;
    IF TG_OP = 'DELETE' AND OLD.status = 'SUBMITTED' THEN
        IF COALESCE(current_setting('velan.purge_active', true), '') <> 'true' THEN
            RAISE EXCEPTION 'SUBMITTED operator_values rows cannot be deleted outside purge';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER operator_values_immutable_guard
    BEFORE UPDATE OR DELETE ON operator_values
    FOR EACH ROW
    EXECUTE FUNCTION trg_operator_values_immutable();
