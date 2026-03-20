-- 法律案例表：FastGPT 知识库同步状态
ALTER TABLE `legal_case`
    ADD COLUMN `fastgpt_sync_status` TINYINT NOT NULL DEFAULT 0 COMMENT 'FastGPT知识库同步：0-未同步，1-同步中，2-成功，3-失败' AFTER `ai_status`,
    ADD COLUMN `fastgpt_synced_at` DATETIME NULL COMMENT '最近一次同步完成时间（成功或失败）' AFTER `fastgpt_sync_status`,
    ADD COLUMN `fastgpt_sync_error` VARCHAR(1000) NULL COMMENT '同步失败原因摘要' AFTER `fastgpt_synced_at`,
    ADD COLUMN `fastgpt_collection_id` VARCHAR(32) NULL COMMENT 'FastGPT 知识库集合ID' AFTER `fastgpt_sync_error`,
    ADD INDEX `idx_fastgpt_sync_status` (`fastgpt_sync_status`);
