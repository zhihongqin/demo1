-- 智能问答功能迁移脚本
-- 数据库：foreign_law_case_system_db

USE foreign_law_case_system_db;

-- 问答会话表
-- 每个用户可以发起多个独立会话，同一 chat_id 的消息共享 FastGPT 上下文
CREATE TABLE IF NOT EXISTS `chat_session` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id`       BIGINT NOT NULL COMMENT '用户ID',
    `chat_id`       VARCHAR(64) NOT NULL COMMENT '会话标识（小程序端生成，传给FastGPT维持上下文）',
    `title`         VARCHAR(200) COMMENT '会话标题（取首条提问，方便历史展示）',
    `message_count` INT DEFAULT 0 COMMENT '消息总条数（含用户+AI）',
    `last_question` VARCHAR(500) COMMENT '最近一次用户提问（预览用）',
    `created_at`    DATETIME COMMENT '创建时间',
    `updated_at`    DATETIME COMMENT '最近活跃时间',
    `is_deleted`    TINYINT DEFAULT 0 COMMENT '逻辑删除：0-正常，1-删除',
    UNIQUE KEY uk_chat_id (`chat_id`),
    INDEX idx_user_id (`user_id`),
    INDEX idx_user_updated (`user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能问答会话表';

-- 问答消息记录表
-- 存储每条用户提问与 AI 回答，方便查阅历史
CREATE TABLE IF NOT EXISTS `chat_message` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `session_id`  BIGINT NOT NULL COMMENT '所属会话ID（关联 chat_session.id）',
    `user_id`     BIGINT NOT NULL COMMENT '用户ID（冗余，方便按用户查询）',
    `role`        VARCHAR(10) NOT NULL COMMENT '角色：user-用户，assistant-AI',
    `content`     TEXT NOT NULL COMMENT '消息内容',
    `tokens_used` INT DEFAULT 0 COMMENT '本次请求消耗的 token 数（仅 assistant 消息记录）',
    `created_at`  DATETIME COMMENT '消息时间',
    `is_deleted`  TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_session_id (`session_id`),
    INDEX idx_user_id (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能问答消息记录表';
