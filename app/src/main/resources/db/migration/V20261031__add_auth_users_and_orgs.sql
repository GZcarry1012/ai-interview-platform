-- 认证授权：组织与用户表（P1 多角色 RBAC）

CREATE TABLE IF NOT EXISTS organizations (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,
    org_type      VARCHAR(32)  NOT NULL, -- PLATFORM / PARTNER
    contact_email VARCHAR(128),
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_organizations_type ON organizations (org_type);

CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(64)  NOT NULL,
    email         VARCHAR(128),
    phone         VARCHAR(32),
    role          VARCHAR(32)  NOT NULL, -- USER / PARTNER_ADMIN / REVIEWER / INTERVIEWER / ADMIN
    org_type      VARCHAR(32),
    org_id        BIGINT REFERENCES organizations (id),
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_username ON users (username);
CREATE INDEX IF NOT EXISTS idx_users_role ON users (role);
CREATE INDEX IF NOT EXISTS idx_users_org_id ON users (org_id);
