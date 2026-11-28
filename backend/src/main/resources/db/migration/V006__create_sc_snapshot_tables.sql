-- Phase 7.4: SC snapshot tables, drawing_documents, and files.sc_id FK (5 tables + 1 FK)

CREATE TABLE sc_product_template_steps (
    id                 UUID PRIMARY KEY,
    sc_id              UUID NOT NULL REFERENCES sc_headers(id) ON DELETE RESTRICT,
    sc_product_id      UUID NOT NULL,
    source_template_id UUID NULL REFERENCES process_templates(id) ON DELETE RESTRICT,
    step_order         INT NOT NULL CHECK (step_order >= 1),
    node_type          VARCHAR(16) NOT NULL CHECK (node_type IN ('DESIGN', 'RM', 'OPERATION', 'QC')),
    operation_id       UUID NULL REFERENCES operations(id) ON DELETE RESTRICT,
    is_extra_process   BOOLEAN NOT NULL DEFAULT FALSE,
    scoped_piece_id    UUID NULL,
    CONSTRAINT uq_sc_pts_id_sc UNIQUE (id, sc_id),
    CONSTRAINT fk_sc_pts_product_sc FOREIGN KEY (sc_product_id, sc_id) REFERENCES sc_products(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT chk_sc_pts_operation_required CHECK (node_type <> 'OPERATION' OR operation_id IS NOT NULL)
);

CREATE INDEX idx_sc_pts_sc_id ON sc_product_template_steps (sc_id);
CREATE INDEX idx_sc_pts_product_order ON sc_product_template_steps (sc_product_id, step_order);
CREATE INDEX idx_sc_pts_source_template_id ON sc_product_template_steps (source_template_id) WHERE source_template_id IS NOT NULL;
CREATE INDEX idx_sc_pts_operation_id ON sc_product_template_steps (operation_id) WHERE operation_id IS NOT NULL;

CREATE TABLE sc_product_node_statuses (
    id               UUID PRIMARY KEY,
    sc_id            UUID NOT NULL REFERENCES sc_headers(id) ON DELETE RESTRICT,
    sc_product_id    UUID NOT NULL,
    snapshot_step_id UUID NOT NULL UNIQUE,
    node_status      VARCHAR(24) NOT NULL,
    updated_by       UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_sc_pns_product_sc FOREIGN KEY (sc_product_id, sc_id) REFERENCES sc_products(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_sc_pns_step_sc FOREIGN KEY (snapshot_step_id, sc_id) REFERENCES sc_product_template_steps(id, sc_id) ON DELETE RESTRICT
);

CREATE INDEX idx_sc_pns_sc_id ON sc_product_node_statuses (sc_id);
CREATE INDEX idx_sc_pns_sc_product_id ON sc_product_node_statuses (sc_product_id);
CREATE INDEX idx_sc_pns_updated_by ON sc_product_node_statuses (updated_by);

CREATE TABLE sc_product_step_parameters (
    id                    UUID PRIMARY KEY,
    sc_id                 UUID NOT NULL REFERENCES sc_headers(id) ON DELETE RESTRICT,
    sc_product_id         UUID NOT NULL,
    snapshot_step_id      UUID NOT NULL,
    source_parameter_id   UUID NULL REFERENCES parameters(id) ON DELETE RESTRICT,
    code                  VARCHAR(64) NOT NULL,
    name                  VARCHAR(150) NOT NULL,
    kind                  VARCHAR(16) NOT NULL CHECK (kind IN ('STATIC', 'DYNAMIC')),
    data_type             VARCHAR(16) NOT NULL CHECK (data_type IN ('NUMERIC', 'YES_NO', 'SINGLE_SELECT', 'TEXT', 'SIGNATURE', 'IMAGE')),
    unit                  VARCHAR(32) NULL,
    options_json          JSONB NULL,
    expected_numeric      NUMERIC(14, 4) NULL,
    expected_bool         BOOLEAN NULL,
    expected_option_code  VARCHAR(64) NULL,
    expected_option_label VARCHAR(150) NULL,
    param_order           INT NOT NULL CHECK (param_order >= 1),
    CONSTRAINT uq_sc_psp_id_sc UNIQUE (id, sc_id),
    CONSTRAINT fk_sc_psp_product_sc FOREIGN KEY (sc_product_id, sc_id) REFERENCES sc_products(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_sc_psp_step_sc FOREIGN KEY (snapshot_step_id, sc_id) REFERENCES sc_product_template_steps(id, sc_id) ON DELETE RESTRICT
);

CREATE INDEX idx_sc_psp_sc_id ON sc_product_step_parameters (sc_id);
CREATE INDEX idx_sc_psp_sc_product_id ON sc_product_step_parameters (sc_product_id);
CREATE INDEX idx_sc_psp_snapshot_step_id ON sc_product_step_parameters (snapshot_step_id);
CREATE INDEX idx_sc_psp_source_parameter_id ON sc_product_step_parameters (source_parameter_id) WHERE source_parameter_id IS NOT NULL;

CREATE TABLE sc_product_dynamic_values (
    id                UUID PRIMARY KEY,
    sc_id             UUID NOT NULL REFERENCES sc_headers(id) ON DELETE RESTRICT,
    sc_product_id     UUID NOT NULL,
    snapshot_step_id  UUID NOT NULL,
    step_parameter_id UUID NOT NULL,
    min_value         NUMERIC(14, 4) NULL,
    max_value         NUMERIC(14, 4) NULL,
    target_value      NUMERIC(14, 4) NOT NULL,
    updated_by        UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sc_pdv_step_param UNIQUE (snapshot_step_id, step_parameter_id),
    CONSTRAINT chk_sc_pdv_min_max CHECK (min_value IS NULL OR max_value IS NULL OR min_value <= max_value),
    CONSTRAINT fk_sc_pdv_product_sc FOREIGN KEY (sc_product_id, sc_id) REFERENCES sc_products(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_sc_pdv_step_sc FOREIGN KEY (snapshot_step_id, sc_id) REFERENCES sc_product_template_steps(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT fk_sc_pdv_param_sc FOREIGN KEY (step_parameter_id, sc_id) REFERENCES sc_product_step_parameters(id, sc_id) ON DELETE RESTRICT
);

CREATE INDEX idx_sc_pdv_sc_id ON sc_product_dynamic_values (sc_id);
CREATE INDEX idx_sc_pdv_sc_product_id ON sc_product_dynamic_values (sc_product_id);
CREATE INDEX idx_sc_pdv_snapshot_step_id ON sc_product_dynamic_values (snapshot_step_id);
CREATE INDEX idx_sc_pdv_step_parameter_id ON sc_product_dynamic_values (step_parameter_id);
CREATE INDEX idx_sc_pdv_updated_by ON sc_product_dynamic_values (updated_by);

CREATE TABLE drawing_documents (
    id            UUID PRIMARY KEY,
    sc_id         UUID NULL REFERENCES sc_headers(id) ON DELETE RESTRICT,
    product_id    UUID NULL REFERENCES products(id) ON DELETE RESTRICT,
    sc_product_id UUID NULL,
    kind          VARCHAR(24) NOT NULL CHECK (kind IN ('PRODUCT_DRAWING', 'SC_DRAWING', 'REVISION_DRAWING')),
    file_id       UUID NOT NULL REFERENCES files(id) ON DELETE RESTRICT,
    created_by    UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_drawing_sc_product_sc FOREIGN KEY (sc_product_id, sc_id) REFERENCES sc_products(id, sc_id) ON DELETE RESTRICT,
    CONSTRAINT chk_drawing_kind_sc CHECK (
        (kind = 'PRODUCT_DRAWING' AND sc_id IS NULL)
        OR (kind IN ('SC_DRAWING', 'REVISION_DRAWING') AND sc_id IS NOT NULL)
    )
);

CREATE INDEX idx_drawing_documents_sc_id ON drawing_documents (sc_id) WHERE sc_id IS NOT NULL;
CREATE INDEX idx_drawing_documents_product_id ON drawing_documents (product_id) WHERE product_id IS NOT NULL;
CREATE INDEX idx_drawing_documents_sc_product_id ON drawing_documents (sc_product_id) WHERE sc_product_id IS NOT NULL;
CREATE INDEX idx_drawing_documents_file_id ON drawing_documents (file_id);
CREATE INDEX idx_drawing_documents_created_by ON drawing_documents (created_by);

ALTER TABLE files
    ADD CONSTRAINT fk_files_sc_id
    FOREIGN KEY (sc_id) REFERENCES sc_headers(id) ON DELETE RESTRICT;
