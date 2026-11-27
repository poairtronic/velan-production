-- Phase 7.4: Order tables (6 tables, no seed data)

CREATE TABLE purchase_orders (
    id         UUID PRIMARY KEY,
    po_number  VARCHAR(64) NOT NULL UNIQUE,
    source     VARCHAR(16) NOT NULL CHECK (source IN ('RM', 'EXCEL', 'MANUAL')),
    rm_po_ref  VARCHAR(128) NULL,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_purchase_orders_created_by ON purchase_orders (created_by);

CREATE TABLE sc_headers (
    id         UUID PRIMARY KEY,
    sc_number  VARCHAR(64) NOT NULL UNIQUE,
    po_id      UUID NULL REFERENCES purchase_orders(id) ON DELETE RESTRICT,
    source     VARCHAR(16) NOT NULL CHECK (source IN ('RM', 'EXCEL', 'MANUAL')),
    status     VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'DONE', 'ARCHIVED', 'PURGED')),
    rm_sc_ref  VARCHAR(128) NULL,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sc_headers_po_id ON sc_headers (po_id) WHERE po_id IS NOT NULL;
CREATE INDEX idx_sc_headers_created_by ON sc_headers (created_by);

CREATE TABLE sc_products (
    id         UUID PRIMARY KEY,
    sc_id      UUID NOT NULL REFERENCES sc_headers(id) ON DELETE RESTRICT,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    quantity   INT NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sc_products_sc_product UNIQUE (sc_id, product_id),
    CONSTRAINT uq_sc_products_id_sc UNIQUE (id, sc_id)
);

CREATE INDEX idx_sc_products_sc_id ON sc_products (sc_id);
CREATE INDEX idx_sc_products_product_id ON sc_products (product_id);

CREATE TABLE sc_revisions (
    id          UUID PRIMARY KEY,
    sc_id       UUID NOT NULL REFERENCES sc_headers(id) ON DELETE RESTRICT,
    revision_no INT NOT NULL CHECK (revision_no >= 1),
    summary     TEXT NOT NULL,
    created_by  UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sc_revisions_sc_rev UNIQUE (sc_id, revision_no)
);

CREATE INDEX idx_sc_revisions_sc_id ON sc_revisions (sc_id);
CREATE INDEX idx_sc_revisions_created_by ON sc_revisions (created_by);

CREATE TABLE import_records (
    id          UUID PRIMARY KEY,
    sc_id       UUID NULL REFERENCES sc_headers(id) ON DELETE RESTRICT,
    source      VARCHAR(16) NOT NULL CHECK (source IN ('RM', 'EXCEL')),
    file_id     UUID NULL REFERENCES files(id) ON DELETE RESTRICT,
    imported_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_import_records_sc_id ON import_records (sc_id) WHERE sc_id IS NOT NULL;
CREATE INDEX idx_import_records_file_id ON import_records (file_id) WHERE file_id IS NOT NULL;
CREATE INDEX idx_import_records_imported_by ON import_records (imported_by);

CREATE TABLE sc_archive_summary (
    id              UUID PRIMARY KEY,
    sc_id           UUID NOT NULL,
    sc_number       VARCHAR(64) NOT NULL,
    status          VARCHAR(16) NOT NULL CHECK (status IN ('EXPORTED', 'APPROVED', 'PURGED')),
    excel_file_id   UUID NOT NULL REFERENCES files(id) ON DELETE RESTRICT,
    pdf_file_id     UUID NOT NULL REFERENCES files(id) ON DELETE RESTRICT,
    piece_count     INT NOT NULL DEFAULT 0 CHECK (piece_count >= 0),
    operation_count INT NOT NULL DEFAULT 0 CHECK (operation_count >= 0),
    value_count     INT NOT NULL DEFAULT 0 CHECK (value_count >= 0),
    archived_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    approved_by     UUID NULL REFERENCES users(id) ON DELETE RESTRICT,
    approved_at     TIMESTAMPTZ NULL,
    purged_at       TIMESTAMPTZ NULL
);

CREATE INDEX idx_sc_archive_summary_excel_file_id ON sc_archive_summary (excel_file_id);
CREATE INDEX idx_sc_archive_summary_pdf_file_id ON sc_archive_summary (pdf_file_id);
CREATE INDEX idx_sc_archive_summary_approved_by ON sc_archive_summary (approved_by) WHERE approved_by IS NOT NULL;
