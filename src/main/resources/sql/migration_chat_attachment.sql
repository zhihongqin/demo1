-- 智能问答附件：任务表与消息表增加 COS 字段
-- 数据库：foreign_law_case_system_db

USE foreign_law_case_system_db;

ALTER TABLE `chat_task`
  ADD COLUMN `file_url` VARCHAR(768) DEFAULT NULL COMMENT '附件 COS 公网 URL' AFTER `question`,
  ADD COLUMN `file_name` VARCHAR(255) DEFAULT NULL COMMENT '附件原始文件名' AFTER `file_url`;

ALTER TABLE `chat_message`
  ADD COLUMN `file_url` VARCHAR(768) DEFAULT NULL COMMENT '用户消息附件 URL' AFTER `content`,
  ADD COLUMN `file_name` VARCHAR(255) DEFAULT NULL COMMENT '用户消息附件名' AFTER `file_url`;
