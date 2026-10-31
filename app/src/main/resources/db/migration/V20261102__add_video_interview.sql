-- P3：视频面试（候选人录制 + 切片上传 + 归档）

-- 候选人面试会话（凭邀约 token 关联，免登录）
CREATE TABLE IF NOT EXISTS candidate_interviews (
    id              BIGSERIAL PRIMARY KEY,
    invitation_id   BIGINT      NOT NULL UNIQUE REFERENCES interview_invitations (id),
    token           VARCHAR(64) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'CREATED', -- CREATED/VIDEO_UPLOADED/COMPLETED
    questions       TEXT,                -- JSON 数组：面试问题清单
    video_file_key  VARCHAR(256),
    video_file_name VARCHAR(256),
    video_file_size BIGINT,
    video_hash      VARCHAR(64),
    duration_seconds INTEGER,
    answers         TEXT,                -- JSON 数组：答卷
    started_at      TIMESTAMP,
    submitted_at    TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_candidate_interview_token ON candidate_interviews (token);

-- 切片上传会话（持久化，服务重启后仍可断点续传）
CREATE TABLE IF NOT EXISTS video_upload_sessions (
    id           VARCHAR(64) PRIMARY KEY,   -- uploadId (UUID)
    token        VARCHAR(64) NOT NULL,
    interview_id BIGINT      NOT NULL REFERENCES candidate_interviews (id),
    file_name    VARCHAR(256) NOT NULL,
    file_size    BIGINT      NOT NULL,
    file_hash    VARCHAR(64) NOT NULL,      -- 整文件 SHA-256（秒传校验）
    total_chunks INTEGER     NOT NULL,
    chunk_size   INTEGER     NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- 已归档文件登记表（SHA-256 → 对象存储键），支撑秒传与去重
CREATE TABLE IF NOT EXISTS uploaded_file_registry (
    file_hash  VARCHAR(64) PRIMARY KEY,
    file_key   VARCHAR(256) NOT NULL,
    file_size  BIGINT       NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 邀约支持自定义面试问题（JSON 数组，可空则用默认题）
ALTER TABLE interview_invitations ADD COLUMN IF NOT EXISTS questions TEXT;
