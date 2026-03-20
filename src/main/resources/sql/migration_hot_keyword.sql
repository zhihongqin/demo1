-- 已有库升级：热门搜索 + 搜索历史支持匿名
-- USE foreign_law_case_system_db;

ALTER TABLE `search_history`
    MODIFY COLUMN `user_id` BIGINT NULL COMMENT '用户ID（未登录为空）';
ALTER TABLE `search_history`
    ADD INDEX `idx_created_at` (`created_at`);

CREATE TABLE IF NOT EXISTS `hot_keyword` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `keyword`       VARCHAR(100) NOT NULL COMMENT '关键词',
    `search_count`  INT DEFAULT 0 COMMENT '统计周期内搜索次数（定时任务更新）',
    `sort_order`    INT DEFAULT 0 COMMENT '手动排序权重，越大越靠前',
    `is_pinned`     TINYINT DEFAULT 0 COMMENT '1=置顶保护，定时任务不自动下线',
    `is_enabled`    TINYINT DEFAULT 1 COMMENT '1=对用户展示',
    `origin`        TINYINT DEFAULT 0 COMMENT '0=统计同步产生 1=管理员手动新增',
    `created_at`    DATETIME COMMENT '创建时间',
    `updated_at`    DATETIME COMMENT '更新时间',
    UNIQUE KEY uk_keyword (`keyword`),
    INDEX idx_enabled_sort (`is_enabled`, `sort_order`, `search_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='热门搜索词表';
