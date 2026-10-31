-- P4：AI 评分与人工复核

ALTER TABLE candidate_interviews
    ADD COLUMN IF NOT EXISTS ai_score_status VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN IF NOT EXISTS ai_score TEXT,
    ADD COLUMN IF NOT EXISTS ai_score_error TEXT,
    ADD COLUMN IF NOT EXISTS human_decision VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS human_comment TEXT,
    ADD COLUMN IF NOT EXISTS human_reviewed_by BIGINT,
    ADD COLUMN IF NOT EXISTS human_reviewed_by_name VARCHAR(64),
    ADD COLUMN IF NOT EXISTS human_reviewed_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_candidate_ai_status ON candidate_interviews (ai_score_status);
