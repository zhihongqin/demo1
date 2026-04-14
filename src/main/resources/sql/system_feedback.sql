-- 系统用户反馈（执行一次即可，与 init.sql 一致）
CREATE TABLE IF NOT EXISTS `system_feedback` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id`       BIGINT NOT NULL COMMENT '提交用户ID',
    `content`       TEXT NOT NULL COMMENT '反馈内容',
    `contact`       VARCHAR(100) COMMENT '联系方式（选填）',
    `client_info`   VARCHAR(500) COMMENT '客户端信息（小程序等）',
    `status`        TINYINT NOT NULL DEFAULT 0 COMMENT '0-未处理 1-已处理',
    `admin_reply`   VARCHAR(1000) COMMENT '管理员处理说明',
    `processed_at`  DATETIME COMMENT '处理时间',
    `processed_by`  BIGINT COMMENT '处理人用户ID',
    `created_at`    DATETIME COMMENT '创建时间',
    `updated_at`    DATETIME COMMENT '更新时间',
    INDEX `idx_status_created` (`status`, `created_at`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统反馈';
