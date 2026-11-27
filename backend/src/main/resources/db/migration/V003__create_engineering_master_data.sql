-- Phase 7.3: Engineering master-data tables (7 tables, no seed data)

CREATE TABLE parameters (
    id                 UUID PRIMARY KEY,
    code               VARCHAR(64) NOT NULL UNIQUE,
    name               VARCHAR(150) NOT NULL,
    kind               VARCHAR(16) NOT NULL CHECK (kind IN ('STATIC', 'DYNAMIC')),
    data_type          VARCHAR(16) NOT NULL CHECK (data_type IN ('NUMERIC', 'YES_NO', 'SINGLE_SELECT', 'TEXT', 'SIGNATURE', 'IMAGE')),
    unit               VARCHAR(32) NULL,
    expected_numeric   NUMERIC(14, 4) NULL,
    expected_bool      BOOLEAN NULL,
    expected_option_id UUID NULL,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_param_expected_numeric CHECK (expected_numeric IS NULL OR (kind = 'STATIC' AND data_type = 'NUMERIC')),
    CONSTRAINT chk_param_static_numeric_requires_expected CHECK ((kind <> 'STATIC' OR data_type <> 'NUMERIC') OR expected_numeric IS NOT NULL),
    CONSTRAINT chk_param_expected_bool CHECK (expected_bool IS NULL OR (kind = 'STATIC' AND data_type = 'YES_NO')),
    CONSTRAINT chk_param_expected_option CHECK (expected_option_id IS NULL OR (kind = 'STATIC' AND data_type = 'SINGLE_SELECT')),
    CONSTRAINT chk_param_dynamic_no_expected CHECK (
        kind <> 'DYNAMIC' OR (expected_numeric IS NULL AND expected_bool IS NULL AND expected_option_id IS NULL)
    )
);

CREATE TABLE parameter_options (
    id           UUID PRIMARY KEY,
    parameter_id UUID NOT NULL REFERENCES parameters(id) ON DELETE RESTRICT,
    code         VARCHAR(64) NOT NULL,
    label        VARCHAR(150) NOT NULL,
    position     INT NOT NULL CHECK (position >= 1),
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_parameter_options_param_code UNIQUE (parameter_id, code),
    CONSTRAINT uq_parameter_options_param_pos UNIQUE (parameter_id, position),
    CONSTRAINT uq_parameter_options_param_id UNIQUE (parameter_id, id)
);

CREATE INDEX idx_parameter_options_parameter_id ON parameter_options (parameter_id);

ALTER TABLE parameters
    ADD CONSTRAINT fk_parameters_expected_option
    FOREIGN KEY (id, expected_option_id) REFERENCES parameter_options(parameter_id, id) ON DELETE RESTRICT;

CREATE INDEX idx_parameters_expected_option_id ON parameters (expected_option_id) WHERE expected_option_id IS NOT NULL;

CREATE TABLE checklists (
    id          UUID PRIMARY KEY,
    code        VARCHAR(64) NOT NULL UNIQUE,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(500) NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE checklist_parameters (
    id           UUID PRIMARY KEY,
    checklist_id UUID NOT NULL REFERENCES checklists(id) ON DELETE RESTRICT,
    parameter_id UUID NOT NULL REFERENCES parameters(id) ON DELETE RESTRICT,
    position     INT NOT NULL CHECK (position >= 1),
    CONSTRAINT uq_checklist_params_param UNIQUE (checklist_id, parameter_id),
    CONSTRAINT uq_checklist_params_pos UNIQUE (checklist_id, position)
);

CREATE INDEX idx_checklist_parameters_checklist_id ON checklist_parameters (checklist_id);
CREATE INDEX idx_checklist_parameters_parameter_id ON checklist_parameters (parameter_id);

CREATE TABLE operations (
    id         UUID PRIMARY KEY,
    code       VARCHAR(64) NOT NULL UNIQUE,
    name       VARCHAR(150) NOT NULL,
    section_id UUID NOT NULL REFERENCES sections(id) ON DELETE RESTRICT,
    kind       VARCHAR(16) NOT NULL CHECK (kind IN ('IN_HOUSE', 'VENDOR', 'QC')),
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_operations_section_id ON operations (section_id);

CREATE TABLE operation_parameters (
    id           UUID PRIMARY KEY,
    operation_id UUID NOT NULL REFERENCES operations(id) ON DELETE RESTRICT,
    parameter_id UUID NOT NULL REFERENCES parameters(id) ON DELETE RESTRICT,
    position     INT NOT NULL CHECK (position >= 1),
    CONSTRAINT uq_operation_params_param UNIQUE (operation_id, parameter_id),
    CONSTRAINT uq_operation_params_pos UNIQUE (operation_id, position)
);

CREATE INDEX idx_operation_parameters_operation_id ON operation_parameters (operation_id);
CREATE INDEX idx_operation_parameters_parameter_id ON operation_parameters (parameter_id);

CREATE TABLE operation_checklists (
    id           UUID PRIMARY KEY,
    operation_id UUID NOT NULL REFERENCES operations(id) ON DELETE RESTRICT,
    checklist_id UUID NOT NULL REFERENCES checklists(id) ON DELETE RESTRICT,
    position     INT NOT NULL CHECK (position >= 1),
    CONSTRAINT uq_operation_checklists_cl UNIQUE (operation_id, checklist_id),
    CONSTRAINT uq_operation_checklists_pos UNIQUE (operation_id, position)
);

CREATE INDEX idx_operation_checklists_operation_id ON operation_checklists (operation_id);
CREATE INDEX idx_operation_checklists_checklist_id ON operation_checklists (checklist_id);
