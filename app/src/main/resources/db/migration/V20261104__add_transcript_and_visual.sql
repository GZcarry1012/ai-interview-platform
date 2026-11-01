-- 多模态：语音转写 + 视觉分析

ALTER TABLE candidate_interviews
    ADD COLUMN IF NOT EXISTS transcript TEXT,
    ADD COLUMN IF NOT EXISTS transcript_status VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN IF NOT EXISTS transcript_error TEXT,
    ADD COLUMN IF NOT EXISTS visual_analysis TEXT,
    ADD COLUMN IF NOT EXISTS visual_status VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN IF NOT EXISTS visual_error TEXT;
