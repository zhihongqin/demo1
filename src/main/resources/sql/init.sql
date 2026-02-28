-- 涉外法律案例查询小程序 数据库初始化脚本
-- 数据库：foreign_law_case_system

CREATE DATABASE IF NOT EXISTS foreign_law_case_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE foreign_law_case_system;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id`         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `openid`     VARCHAR(100) NOT NULL UNIQUE COMMENT '微信openid',
    `unionid`    VARCHAR(100) COMMENT '微信unionid',
    `nickname`   VARCHAR(50) COMMENT '昵称',
    `avatar_url` VARCHAR(500) COMMENT '头像URL',
    `phone`      VARCHAR(20) COMMENT '手机号',
    `role`       TINYINT DEFAULT 0 COMMENT '角色：0-普通用户，1-管理员',
    `status`     TINYINT DEFAULT 0 COMMENT '状态：0-正常，1-禁用',
    `created_at` DATETIME COMMENT '创建时间',
    `updated_at` DATETIME COMMENT '更新时间',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除：0-正常，1-删除',
    INDEX idx_openid (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 法律案例主表
CREATE TABLE IF NOT EXISTS `legal_case` (
    `id`               BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `case_no`          VARCHAR(100) COMMENT '案例编号',
    `title_zh`         VARCHAR(500) COMMENT '案例标题（中文）',
    `title_en`         VARCHAR(500) COMMENT '案例标题（英文）',
    `case_reason`      VARCHAR(200) COMMENT '案由',
    `case_type`        TINYINT COMMENT '案件类型：1-民事，2-刑事，3-行政，4-商事',
    `country`          VARCHAR(100) COMMENT '所属国家/地区',
    `court`            VARCHAR(200) COMMENT '审理法院',
    `judgment_date`    DATE COMMENT '判决日期',
    `content_en`       LONGTEXT COMMENT '英文原文',
    `content_zh`       LONGTEXT COMMENT '中文翻译',
    `dispute_focus`    TEXT COMMENT '争议焦点',
    `judgment_result`  TEXT COMMENT '判决结果',
    `summary_zh`       TEXT COMMENT '核心摘要（中文）',
    `importance_score` INT DEFAULT 0 COMMENT '重要性评分（0-100）',
    `score_reason`     TEXT COMMENT '评分理由',
    `keywords`         VARCHAR(500) COMMENT '关键词（逗号分隔）',
    `legal_provisions` VARCHAR(1000) COMMENT '涉及法律条文',
    `ai_status`        TINYINT DEFAULT 0 COMMENT 'AI状态：0-待处理，1-处理中，2-已完成，3-失败',
    `view_count`       INT DEFAULT 0 COMMENT '查看次数',
    `favorite_count`   INT DEFAULT 0 COMMENT '收藏次数',
    `created_at`       DATETIME COMMENT '创建时间',
    `updated_at`       DATETIME COMMENT '更新时间',
    `is_deleted`       TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_case_type (`case_type`),
    INDEX idx_country (`country`),
    INDEX idx_importance_score (`importance_score`),
    FULLTEXT INDEX ft_search (`title_zh`, `title_en`, `case_reason`, `keywords`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='法律案例主表';

-- 案例翻译记录表
CREATE TABLE IF NOT EXISTS `case_translation` (
    `id`                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    `case_id`            BIGINT NOT NULL COMMENT '案例ID',
    `source_lang`        VARCHAR(10) DEFAULT 'en' COMMENT '原文语言',
    `target_lang`        VARCHAR(10) DEFAULT 'zh' COMMENT '目标语言',
    `translated_content` LONGTEXT COMMENT '翻译内容',
    `status`             TINYINT DEFAULT 0 COMMENT '状态：0-待翻译，1-翻译中，2-完成，3-失败',
    `ai_model`           VARCHAR(100) COMMENT '使用模型',
    `token_used`         INT COMMENT '消耗token',
    `error_msg`          TEXT COMMENT '错误信息',
    `created_at`         DATETIME COMMENT '创建时间',
    `updated_at`         DATETIME COMMENT '更新时间',
    INDEX idx_case_id (`case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='案例翻译记录表';

-- 案例摘要表
CREATE TABLE IF NOT EXISTS `case_summary` (
    `id`               BIGINT AUTO_INCREMENT PRIMARY KEY,
    `case_id`          BIGINT NOT NULL COMMENT '案例ID',
    `case_reason`      TEXT COMMENT '案由摘要',
    `dispute_focus`    TEXT COMMENT '争议焦点',
    `judgment_result`  TEXT COMMENT '判决结果摘要',
    `key_points`       TEXT COMMENT '核心要点',
    `status`           TINYINT DEFAULT 0 COMMENT '状态：0-待提取，1-提取中，2-完成，3-失败',
    `error_msg`        TEXT COMMENT '错误信息',
    `created_at`       DATETIME COMMENT '创建时间',
    `updated_at`       DATETIME COMMENT '更新时间',
    INDEX idx_case_id (`case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='案例摘要表';

-- 案例评分表
CREATE TABLE IF NOT EXISTS `case_score` (
    `id`               BIGINT AUTO_INCREMENT PRIMARY KEY,
    `case_id`          BIGINT NOT NULL COMMENT '案例ID',
    `importance_score` INT COMMENT '重要性评分',
    `influence_score`  INT COMMENT '影响力评分',
    `reference_score`  INT COMMENT '参考价值评分',
    `total_score`      INT COMMENT '综合评分',
    `score_reason`     TEXT COMMENT '评分理由',
    `score_tags`       VARCHAR(500) COMMENT '评分标签',
    `status`           TINYINT DEFAULT 0 COMMENT '状态：0-待评分，1-评分中，2-完成，3-失败',
    `error_msg`        TEXT COMMENT '错误信息',
    `created_at`       DATETIME COMMENT '创建时间',
    `updated_at`       DATETIME COMMENT '更新时间',
    INDEX idx_case_id (`case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='案例评分表';

-- 用户收藏表
CREATE TABLE IF NOT EXISTS `user_favorite` (
    `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`    BIGINT NOT NULL COMMENT '用户ID',
    `case_id`    BIGINT NOT NULL COMMENT '案例ID',
    `remark`     VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME COMMENT '创建时间',
    UNIQUE KEY uk_user_case (`user_id`, `case_id`),
    INDEX idx_user_id (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏表';

-- 搜索历史表
CREATE TABLE IF NOT EXISTS `search_history` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`      BIGINT NOT NULL COMMENT '用户ID',
    `keyword`      VARCHAR(200) NOT NULL COMMENT '搜索关键词',
    `search_type`  TINYINT DEFAULT 1 COMMENT '搜索类型：1-全文，2-案由，3-国家',
    `result_count` INT COMMENT '结果数量',
    `created_at`   DATETIME COMMENT '创建时间',
    INDEX idx_user_id (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索历史表';
