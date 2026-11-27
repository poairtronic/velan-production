-- Phase 7.6: Vendor tables and piece_operations.vendor_dc_tracker_id FK (2 tables + 1 ALTER)

CREATE TABLE vendors (
    id         UUID PRIMARY KEY,
    code       VARCHAR(64) NOT NULL,
    name       VARCHAR(150) NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_vendors_code UNIQUE (code)
);

CREATE TABLE vendor_dc_trackers (
    id            UUID PRIMARY KEY,
    sc_id         UUID NOT NULL,
    vendor_id     UUID NOT NULL,
    dc_number     VARCHAR(64) NOT NULL,
    quantity_sent INT NOT NULL CHECK (quantity_sent > 0),
    status        VARCHAR(24) NOT NULL CHECK (status IN ('RECEIVED', 'PARTIAL', 'NOT_RECEIVED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_vendor_dc_vendor_dc_number UNIQUE (vendor_id, dc_number),
    CONSTRAINT uq_vendor_dc_id_sc UNIQUE (id, sc_id),
    CONSTRAINT fk_vendor_dc_sc_headers FOREIGN KEY (sc_id) REFERENCES sc_headers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_vendor_dc_vendors FOREIGN KEY (vendor_id) REFERENCES vendors(id) ON DELETE RESTRICT
);

CREATE INDEX idx_vendor_dc_trackers_sc_id ON vendor_dc_trackers (sc_id);
CREATE INDEX idx_vendor_dc_trackers_vendor_id ON vendor_dc_trackers (vendor_id);

ALTER TABLE piece_operations
    ADD COLUMN vendor_dc_tracker_id UUID NULL,
    ADD CONSTRAINT fk_piece_ops_vendor_dc_sc
        FOREIGN KEY (vendor_dc_tracker_id, sc_id)
        REFERENCES vendor_dc_trackers(id, sc_id) ON DELETE RESTRICT;

CREATE INDEX idx_piece_operations_vendor_dc_tracker_id
    ON piece_operations (vendor_dc_tracker_id)
    WHERE vendor_dc_tracker_id IS NOT NULL;
