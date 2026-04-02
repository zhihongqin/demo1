-- 异步问答任务表
CREATE TABLE IF NOT EXISTS `chat_task` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `task_id`    VARCHAR(36)  NOT NULL                  COMMENT '任务UUID，前端用于轮询',
  `session_id` BIGINT       NOT NULL                  COMMENT '所属会话ID',
  `user_id`    BIGINT       NOT NULL                  COMMENT '发起用户ID',
  `question`   TEXT         NOT NULL                  COMMENT '用户提问内容',
  `answer`     TEXT         DEFAULT NULL              COMMENT 'AI回答（完成后写入）',
  `status`     TINYINT      NOT NULL DEFAULT 0        COMMENT '0=处理中 1=完成 2=失败',
  `error_msg`  VARCHAR(500) DEFAULT NULL              COMMENT '失败原因',
  `created_at` DATETIME     DEFAULT NULL,
  `updated_at` DATETIME     DEFAULT NULL,
  `is_deleted` TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id`    (`task_id`),
  KEY        `idx_user_task` (`user_id`, `status`),
  KEY        `idx_session`   (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异步问答任务';
