-- Phase 7.4: Engineering products and process templates (3 tables, no seed data)

CREATE TABLE process_templates (
    id          UUID PRIMARY KEY,
    code        VARCHAR(64) NOT NULL UNIQUE,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(500) NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE products (
    id                          UUID PRIMARY KEY,
    name                        VARCHAR(150) NOT NULL,
    rm_product_ref              VARCHAR(128) NULL,
    default_process_template_id UUID NULL REFERENCES process_templates(id) ON DELETE RESTRICT,
    active                      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_products_name_norm ON products (LOWER(TRIM(name)));
CREATE INDEX idx_products_default_template_id ON products (default_process_template_id) WHERE default_process_template_id IS NOT NULL;

CREATE TABLE process_template_steps (
    id           UUID PRIMARY KEY,
    template_id  UUID NOT NULL REFERENCES process_templates(id) ON DELETE RESTRICT,
    position     INT NOT NULL CHECK (position >= 1),
    node_type    VARCHAR(16) NOT NULL CHECK (node_type IN ('DESIGN', 'RM', 'OPERATION', 'QC')),
    operation_id UUID NULL REFERENCES operations(id) ON DELETE RESTRICT,
    CONSTRAINT uq_process_template_steps_pos UNIQUE (template_id, position),
    CONSTRAINT chk_pts_operation_required CHECK (node_type <> 'OPERATION' OR operation_id IS NOT NULL)
);

CREATE INDEX idx_process_template_steps_template_id ON process_template_steps (template_id);
CREATE INDEX idx_process_template_steps_operation_id ON process_template_steps (operation_id) WHERE operation_id IS NOT NULL;
