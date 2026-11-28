-- Phase 7.5: Quality tables (qc_inspections, measurement_values)

CREATE TABLE qc_inspections (
    id                 UUID PRIMARY KEY,
    sc_id              UUID NOT NULL REFERENCES sc_headers(id) ON DELETE RESTRICT,
    piece_id           UUID NOT NULL,
    piece_operation_id UUID NOT NULL,
    inspector_id       UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    decision           VARCHAR(24) NOT NULL,
    notes              TEXT NULL,
    inspected_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_qc_inspections_id_sc UNIQUE (id, sc_id),
    CONSTRAINT fk_qc_insp_piece_sc FOREIGN KEY (piece_id, sc_id) REFERENCES pieces(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_qc_insp_op_sc FOREIGN KEY (piece_operation_id, sc_id) REFERENCES piece_operations(id, sc_id) ON DELETE RESTRICT
);

CREATE INDEX idx_qc_inspections_sc_id ON qc_inspections (sc_id);
CREATE INDEX idx_qc_inspections_piece_id ON qc_inspections (piece_id);
CREATE INDEX idx_qc_inspections_op_id ON qc_inspections (piece_operation_id);
CREATE INDEX idx_qc_inspections_inspector_id ON qc_inspections (inspector_id);

CREATE TABLE measurement_values (
    id                 UUID PRIMARY KEY,
    sc_id              UUID NOT NULL REFERENCES sc_headers(id) ON DELETE RESTRICT,
    qc_inspection_id   UUID NOT NULL,
    piece_id           UUID NOT NULL,
    piece_operation_id UUID NOT NULL,
    snapshot_step_id   UUID NOT NULL,
    step_parameter_id  UUID NOT NULL,
    val_numeric        NUMERIC(14, 4) NULL,
    val_bool           BOOLEAN NULL,
    val_text           TEXT NULL,
    val_option_code    VARCHAR(64) NULL,
    val_option_label   VARCHAR(150) NULL,
    val_file_id        UUID NULL REFERENCES files(id) ON DELETE RESTRICT,
    judged_min         NUMERIC(14, 4) NULL,
    judged_max         NUMERIC(14, 4) NULL,
    judged_target      NUMERIC(14, 4) NULL,
    result             VARCHAR(16) NOT NULL CHECK (result IN ('PASS', 'FAIL', 'NA')),
    recorded_by        UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    recorded_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_meas_qc_insp_sc FOREIGN KEY (qc_inspection_id, sc_id) REFERENCES qc_inspections(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_meas_piece_sc FOREIGN KEY (piece_id, sc_id) REFERENCES pieces(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_meas_op_sc FOREIGN KEY (piece_operation_id, sc_id) REFERENCES piece_operations(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_meas_step_sc FOREIGN KEY (snapshot_step_id, sc_id) REFERENCES sc_product_template_steps(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_meas_param_sc FOREIGN KEY (step_parameter_id, sc_id) REFERENCES sc_product_step_parameters(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT chk_meas_one_value CHECK (
        (CASE WHEN val_numeric IS NOT NULL THEN 1 ELSE 0 END
         + CASE WHEN val_bool IS NOT NULL THEN 1 ELSE 0 END
         + CASE WHEN val_text IS NOT NULL THEN 1 ELSE 0 END
         + CASE WHEN val_option_code IS NOT NULL THEN 1 ELSE 0 END
         + CASE WHEN val_file_id IS NOT NULL THEN 1 ELSE 0 END) = 1
        AND (val_numeric IS NULL OR judged_target IS NOT NULL)
    )
);

CREATE INDEX idx_measurement_values_sc_id ON measurement_values (sc_id);
CREATE INDEX idx_measurement_values_qc_insp_id ON measurement_values (qc_inspection_id);
CREATE INDEX idx_measurement_values_piece_id ON measurement_values (piece_id);
CREATE INDEX idx_measurement_values_op_id ON measurement_values (piece_operation_id);
CREATE INDEX idx_measurement_values_step_id ON measurement_values (snapshot_step_id);
CREATE INDEX idx_measurement_values_param_id ON measurement_values (step_parameter_id);
CREATE INDEX idx_measurement_values_file_id ON measurement_values (val_file_id) WHERE val_file_id IS NOT NULL;
CREATE INDEX idx_measurement_values_recorded_by ON measurement_values (recorded_by);
