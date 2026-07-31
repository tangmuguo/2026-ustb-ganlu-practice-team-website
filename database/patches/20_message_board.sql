-- ============================================
-- 20_message_board.sql
-- 留言板索引优化 + 数据完整性补丁
-- 执行顺序：在 ganlu.sql 基础上追加执行
-- ============================================

-- 1. 先处理孤儿数据（删除不存在用户的留言/回复，避免加外键时报错）
-- 先执行下方SELECT核查数据数量，确认无误后，再取消注释执行DELETE
-- SELECT COUNT(*) FROM reply WHERE message_id NOT IN (SELECT id FROM message);
-- SELECT COUNT(*) FROM message WHERE user_id NOT IN (SELECT id FROM user);
-- DELETE FROM reply WHERE message_id NOT IN (SELECT id FROM message);
-- DELETE FROM message WHERE user_id NOT IN (SELECT id FROM user);

-- 2. 给 message 加组合索引（分页查询用）
-- 查询逻辑：WHERE status = ? ORDER BY create_time DESC, id DESC
ALTER TABLE message 
ADD INDEX idx_message_status_create_time (status, create_time DESC, id DESC);

-- 3. 给 reply 加组合索引（根据留言批量查询回复）
-- 查询逻辑：WHERE message_id = ? AND status = ? ORDER BY create_time DESC, id DESC
ALTER TABLE reply 
ADD INDEX idx_reply_message_status_create (message_id, status, create_time DESC, id DESC);

-- 4. 外键约束（可选，团队讨论确定是否启用，默认注释状态）
-- 约束规则：RESTRICT，禁止级联删除，避免数据意外清除
-- ALTER TABLE message ADD CONSTRAINT fk_message_user 
--   FOREIGN KEY (user_id) REFERENCES user(id);

-- ALTER TABLE reply ADD CONSTRAINT fk_reply_message 
--   FOREIGN KEY (message_id) REFERENCES message(id);

-- ALTER TABLE reply ADD CONSTRAINT fk_reply_user 
--   FOREIGN KEY (user_id) REFERENCES user(id);