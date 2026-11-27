-- Phase 7.3: IAM tables (7 tables, no seed data; roles seeded in Phase 7.7)

CREATE TABLE departments (
    id         UUID PRIMARY KEY,
    code       VARCHAR(64) NOT NULL UNIQUE,
    name       VARCHAR(150) NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE sections (
    id         UUID PRIMARY KEY,
    code       VARCHAR(64) NOT NULL UNIQUE,
    name       VARCHAR(150) NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE roles (
    id          UUID PRIMARY KEY,
    name        VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(255) NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE role_permissions (
    id         UUID PRIMARY KEY,
    role_id    UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    permission VARCHAR(128) NOT NULL,
    CONSTRAINT uq_role_permissions_role_perm UNIQUE (role_id, permission)
);

CREATE INDEX idx_role_permissions_role_id ON role_permissions (role_id);

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    login         VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    employee_id   VARCHAR(64) NOT NULL UNIQUE,
    department_id UUID NOT NULL REFERENCES departments(id) ON DELETE RESTRICT,
    phone         VARCHAR(32) NULL,
    email         VARCHAR(255) NULL,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_users_login_lower ON users (LOWER(login));
CREATE INDEX idx_users_department_id ON users (department_id);

CREATE TABLE user_roles (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    role_id     UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    assigned_by UUID NULL REFERENCES users(id) ON DELETE RESTRICT,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_roles_user_role UNIQUE (user_id, role_id)
);

CREATE INDEX idx_user_roles_user_id ON user_roles (user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);
CREATE INDEX idx_user_roles_assigned_by ON user_roles (assigned_by) WHERE assigned_by IS NOT NULL;

CREATE TABLE user_permission_overrides (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    permission VARCHAR(128) NOT NULL,
    effect     VARCHAR(16) NOT NULL CHECK (effect IN ('ALLOW', 'DENY')),
    reason     VARCHAR(255) NULL,
    granted_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_perm_override UNIQUE (user_id, permission)
);

CREATE INDEX idx_user_perm_overrides_user_id ON user_permission_overrides (user_id);
CREATE INDEX idx_user_perm_overrides_granted_by ON user_permission_overrides (granted_by);

CREATE TABLE user_sections (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    section_id  UUID NOT NULL REFERENCES sections(id) ON DELETE RESTRICT,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_sections_user_section UNIQUE (user_id, section_id)
);

CREATE INDEX idx_user_sections_user_id ON user_sections (user_id);
CREATE INDEX idx_user_sections_section_id ON user_sections (section_id);
