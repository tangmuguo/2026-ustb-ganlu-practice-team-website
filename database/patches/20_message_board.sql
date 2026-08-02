-- 留言板索引补丁。执行前请备份数据库。
-- 现有字段已经满足逻辑删除；此补丁补充分页和批量回复查询所需索引。

SET @has_message_status_time := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'message' AND index_name = 'idx_message_status_time'
);
SET @sql := IF(@has_message_status_time = 0,
  'ALTER TABLE `message` ADD INDEX `idx_message_status_time` (`status`, `create_time`, `id`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_reply_message_status_time := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'reply' AND index_name = 'idx_reply_message_status_time'
);
SET @sql := IF(@has_reply_message_status_time = 0,
  'ALTER TABLE `reply` ADD INDEX `idx_reply_message_status_time` (`message_id`, `status`, `create_time`, `id`)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
