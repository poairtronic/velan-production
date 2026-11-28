-- Phase 7.6: QC Exception, PM Disposition, and Piece Replacement Lineage tables (3 tables)

CREATE TABLE qc_exceptions (
    id                 UUID PRIMARY KEY,
    sc_id              UUID NOT NULL,
    piece_id           UUID NOT NULL,
    piece_operation_id UUID NOT NULL,
    exception_type     VARCHAR(32) NOT NULL CHECK (exception_type IN ('QC_FAIL', 'DIMENSION_OUT', 'VISUAL_DEFECT', 'OTHER')),
    reason             TEXT NOT NULL,
    status             VARCHAR(24) NOT NULL CHECK (status IN ('OPEN', 'RESOLVED', 'CANCELLED')),
    ecr_status         VARCHAR(24) NOT NULL CHECK (ecr_status IN ('PENDING', 'SENT', 'FAILED')),
    ecr_reference      VARCHAR(128) NULL,
    raised_by          UUID NOT NULL,
    raised_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_qc_exceptions_id_sc UNIQUE (id, sc_id),
    CONSTRAINT fk_qc_exceptions_sc_headers FOREIGN KEY (sc_id) REFERENCES sc_headers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_qc_exceptions_pieces FOREIGN KEY (piece_id, sc_id) REFERENCES pieces(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_qc_exceptions_piece_ops FOREIGN KEY (piece_operation_id, sc_id) REFERENCES piece_operations(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_qc_exceptions_users FOREIGN KEY (raised_by) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_qc_exceptions_sc_id ON qc_exceptions (sc_id);
CREATE INDEX idx_qc_exceptions_piece_id ON qc_exceptions (piece_id);
CREATE INDEX idx_qc_exceptions_piece_op_id ON qc_exceptions (piece_operation_id);
CREATE INDEX idx_qc_exceptions_raised_by ON qc_exceptions (raised_by);

CREATE TABLE pm_dispositions (
    id                 UUID PRIMARY KEY,
    sc_id              UUID NOT NULL,
    qc_exception_id    UUID NOT NULL,
    piece_id           UUID NOT NULL,
    piece_operation_id UUID NOT NULL,
    disposition        VARCHAR(24) NOT NULL CHECK (disposition IN ('CONTINUE', 'EXTRA_PROCESS', 'RETURN', 'REJECT_AND_RESTART')),
    reason             TEXT NOT NULL,
    decided_by         UUID NOT NULL,
    decided_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pm_disp_sc_headers FOREIGN KEY (sc_id) REFERENCES sc_headers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pm_disp_qc_exc FOREIGN KEY (qc_exception_id, sc_id) REFERENCES qc_exceptions(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_pm_disp_pieces FOREIGN KEY (piece_id, sc_id) REFERENCES pieces(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_pm_disp_piece_ops FOREIGN KEY (piece_operation_id, sc_id) REFERENCES piece_operations(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_pm_disp_users FOREIGN KEY (decided_by) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_pm_dispositions_sc_id ON pm_dispositions (sc_id);
CREATE INDEX idx_pm_dispositions_qc_exception_id ON pm_dispositions (qc_exception_id);
CREATE INDEX idx_pm_dispositions_piece_id ON pm_dispositions (piece_id);
CREATE INDEX idx_pm_dispositions_piece_op_id ON pm_dispositions (piece_operation_id);
CREATE INDEX idx_pm_dispositions_decided_by ON pm_dispositions (decided_by);

CREATE TABLE piece_replacement_lineage (
    id                   UUID PRIMARY KEY,
    sc_id                UUID NOT NULL,
    qc_exception_id      UUID NOT NULL,
    original_piece_id    UUID NOT NULL,
    replacement_piece_id UUID NOT NULL,
    created_by           UUID NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_prl_replacement_piece UNIQUE (replacement_piece_id),
    CONSTRAINT fk_prl_sc_headers FOREIGN KEY (sc_id) REFERENCES sc_headers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_prl_qc_exc FOREIGN KEY (qc_exception_id, sc_id) REFERENCES qc_exceptions(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_prl_orig_piece FOREIGN KEY (original_piece_id, sc_id) REFERENCES pieces(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_prl_repl_piece FOREIGN KEY (replacement_piece_id, sc_id) REFERENCES pieces(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_prl_users FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_prl_sc_id ON piece_replacement_lineage (sc_id);
CREATE INDEX idx_prl_qc_exception_id ON piece_replacement_lineage (qc_exception_id);
CREATE INDEX idx_prl_original_piece_id ON piece_replacement_lineage (original_piece_id);
CREATE INDEX idx_prl_created_by ON piece_replacement_lineage (created_by);
