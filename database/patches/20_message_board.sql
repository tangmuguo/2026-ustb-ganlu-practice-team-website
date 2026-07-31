-- ============================================
-- 20_message_board.sql
-- 互动留言板索引优化 + 数据完整性补丁
-- 执行顺序：在 ganlu.sql 基础上追加执行
-- 约束说明：全程使用status逻辑软删除，不物理清除历史审计数据
-- 不启用任何 ON DELETE CASCADE 级联删除，防止丢失历史留言、回复
-- ============================================

-- 1. 孤儿数据检查（必须先SELECT核查数量，确认后再手动放开DELETE执行清理）
-- 核查回复关联不存在留言的数据
SELECT COUNT(*) AS orphan_reply_count FROM reply WHERE message_id NOT IN (SELECT id FROM message);
-- 核查留言关联不存在用户的数据
SELECT COUNT(*) AS orphan_message_count FROM message WHERE user_id NOT IN (SELECT id FROM user);

-- !!! 确认上面查询数量无误，再取消下面两行注释执行清理孤儿数据
-- DELETE FROM reply WHERE message_id NOT IN (SELECT id FROM message);
-- DELETE FROM message WHERE user_id NOT IN (SELECT id FROM user);

-- 2. message 分页查询组合索引：WHERE status = ? ORDER BY create_time DESC, id DESC
-- 先判断索引不存在再创建，避免重复执行报错
SET @existIdxMsg = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'message' AND INDEX_NAME = 'idx_message_status_ctime');
SET @sqlMsg = IF(@existIdxMsg = 0,
    'ALTER TABLE message ADD INDEX idx_message_status_ctime (status, create_time DESC, id DESC);',
    'SELECT ''索引 idx_message_status_ctime 已存在，跳过创建'' AS notice;');
PREPARE stmt FROM @sqlMsg;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. reply批量查询组合索引：WHERE message_id IN(...) AND status = ? ORDER BY create_time DESC, id DESC
SET @existIdxReply = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reply' AND INDEX_NAME = 'idx_reply_mid_status_ctime');
SET @sqlReply = IF(@existIdxReply = 0,
    'ALTER TABLE reply ADD INDEX idx_reply_mid_status_ctime (message_id, status, create_time DESC, id DESC);',
    'SELECT ''索引 idx_reply_mid_status_ctime 已存在，跳过创建'' AS notice;');
PREPARE stmt FROM @sqlReply;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. 外键约束（可选，保持注释状态，团队确认后再启用）
-- 约束规则：RESTRICT，禁止级联物理删除，保护历史审计数据
-- ALTER TABLE message ADD CONSTRAINT fk_message_user
--   FOREIGN KEY (user_id) REFERENCES user(id);

-- ALTER TABLE reply ADD CONSTRAINT fk_reply_message
--   FOREIGN KEY (message_id) REFERENCES message(id);

-- ALTER TABLE reply ADD CONSTRAINT fk_reply_user
--   FOREIGN KEY (user_id) REFERENCES user(id);

/*
补充业务说明（任务单要求）：
1. 采用 status 字段实现逻辑软删除，不会物理删除任何历史数据，满足审计留存要求；
2. 删除留言仅更新 message.status=0；查询接口自动过滤 status=0 数据，底层回复记录完整保留；
3. 所有列表排序统一 create_time DESC, id DESC；id作为第二排序键，避免同一时间分页数据抖动；
4. 不配置任何级联删除外键，杜绝误操作批量清除历史留言、回复；
5. 索引完全匹配业务SQL：留言分页查询、根据留言ID列表批量拉取回复，解决N+1查询性能问题。
*/