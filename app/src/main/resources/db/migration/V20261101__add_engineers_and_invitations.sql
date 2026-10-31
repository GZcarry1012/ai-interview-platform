-- P2：工程师准入 + 审计日志 + 面试邀约

CREATE TABLE IF NOT EXISTS engineers (
    id               BIGSERIAL PRIMARY KEY,
    org_id           BIGINT       NOT NULL REFERENCES organizations (id),
    name             VARCHAR(64)  NOT NULL,
    phone            VARCHAR(32),
    email            VARCHAR(128) NOT NULL,
    position         VARCHAR(64)  NOT NULL,
    experience_years INTEGER,
    education        VARCHAR(32),
    skills           TEXT,
    resume_file_key  VARCHAR(256) NOT NULL,
    resume_file_name VARCHAR(256),
    resume_file_size BIGINT,
    resume_text      TEXT,
    status           VARCHAR(32)  NOT NULL DEFAULT 'PENDING_REVIEW',
    review_comment   TEXT,
    reviewed_by      BIGINT,
    reviewed_by_name VARCHAR(64),
    reviewed_at      TIMESTAMP,
    created_by       BIGINT,
    created_by_name  VARCHAR(64),
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_engineers_org ON engineers (org_id);
CREATE INDEX IF NOT EXISTS idx_engineers_status ON engineers (status);

CREATE TABLE IF NOT EXISTS engineer_audit_logs (
    id             BIGSERIAL PRIMARY KEY,
    engineer_id    BIGINT      NOT NULL REFERENCES engineers (id),
    action         VARCHAR(32) NOT NULL,
    operator_id    BIGINT,
    operator_name  VARCHAR(64),
    operator_source VARCHAR(64),
    comment        TEXT,
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS interview_invitations (
    id              BIGSERIAL PRIMARY KEY,
    engineer_id     BIGINT      NOT NULL REFERENCES engineers (id),
    token           VARCHAR(64) NOT NULL,
    scheduled_time  TIMESTAMP   NOT NULL,
    position        VARCHAR(128),
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    mail_status     VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    mail_retry_count INTEGER    NOT NULL DEFAULT 0,
    mail_error      TEXT,
    mail_sent_at    TIMESTAMP,
    invited_email   VARCHAR(128) NOT NULL,
    created_by      BIGINT,
    created_by_name VARCHAR(64),
    confirmed_at    TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_invitation_token ON interview_invitations (token);
CREATE INDEX IF NOT EXISTS idx_invitation_engineer ON interview_invitations (engineer_id);
CREATE INDEX IF NOT EXISTS idx_invitation_mail_status ON interview_invitations (mail_status);
