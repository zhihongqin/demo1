-- 案例笔记：每用户每案例一条（执行一次即可，与 init.sql 内容一致）
CREATE TABLE IF NOT EXISTS `case_user_note` (
    `id`         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id`    BIGINT NOT NULL COMMENT '用户ID',
    `case_id`    BIGINT NOT NULL COMMENT '案例ID',
    `content`    TEXT NOT NULL COMMENT '笔记正文',
    `created_at` DATETIME COMMENT '创建时间',
    `updated_at` DATETIME COMMENT '更新时间',
    UNIQUE KEY `uk_user_case` (`user_id`, `case_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_case_id` (`case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户案例笔记';
