-- 案例爬取任务记录（管理员查看历史）
-- 执行：在 foreign_law_case_system_db 中运行本脚本，或依赖 init.sql 中的同步定义

CREATE TABLE IF NOT EXISTS `crawl_job_record` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `crawl_type`    VARCHAR(32)  NOT NULL COMMENT '采集类型：COURTLISTENER / JAPAN_COURTS',
    `params_json`   LONGTEXT     NOT NULL COMMENT '采集参数 JSON 文本',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0-运行中 1-已成功结束 2-已失败结束',
    `saved_count`   INT          NULL COMMENT '入库条数（日本爬虫可能无法统计则为空）',
    `error_message` VARCHAR(1000) NULL COMMENT '失败原因',
    `started_by`    BIGINT       NULL COMMENT '触发用户ID（定时任务为空）',
    `started_at`    DATETIME     NOT NULL COMMENT '开始时间',
    `finished_at`   DATETIME     NULL COMMENT '结束时间',
    INDEX idx_type_started (`crawl_type`, `started_at`),
    INDEX idx_status_started (`status`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='案例爬取任务记录';
