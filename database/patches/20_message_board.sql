-- ============================================
-- 20_message_board.sql
-- 互动留言板索引优化 + 数据完整性补丁
-- 执行方式：在 ganlu.sql 基础上**增量追加执行**
-- 核心约束：不 DROP 任何原表、不清除已有留言数据，全程使用 status 逻辑软删除
-- 禁用所有 ON DELETE CASCADE 级联删除，保护历史审计数据
-- ============================================

-- ========== 1. 孤儿数据预处理（添加外键前必须执行） ==========
-- 对应任务单：添加外键前先处理孤儿数据
-- 先核查数量，确认无误后再执行清理
SELECT COUNT(*) AS orphan_reply_count FROM reply WHERE message_id NOT IN (SELECT id FROM message);
SELECT COUNT(*) AS orphan_message_count FROM message WHERE user_id NOT IN (SELECT id FROM user);
SELECT COUNT(*) AS orphan_reply_user_count FROM reply WHERE user_id NOT IN (SELECT id FROM user);

-- !!! 确认上述查询结果无误后，再取消下方注释执行清理
-- DELETE FROM reply WHERE message_id NOT IN (SELECT id FROM message);
-- DELETE FROM message WHERE user_id NOT IN (SELECT id FROM user);
-- DELETE FROM reply WHERE user_id NOT IN (SELECT id FROM user);

-- ========== 2. message 表组合索引 ==========
-- 对应任务单：message(status, create_time) 组合索引
-- 匹配业务SQL：WHERE status = 1 ORDER BY create_time DESC, id DESC
SET @existIdxMsg = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'message' AND INDEX_NAME = 'idx_message_status_create_time');
SET @sqlMsg = IF(@existIdxMsg = 0,
    'ALTER TABLE message ADD INDEX idx_message_status_create_time (status, create_time ASC, id ASC);',
    'SELECT ''索引 idx_message_status_create_time 已存在，跳过创建'' AS notice;');
PREPARE stmt FROM @sqlMsg;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ========== 3. reply 表组合索引 ==========
-- 对应任务单：reply(message_id, status, create_time) 组合索引
-- 匹配业务SQL：WHERE message_id IN (...) AND status = 1 ORDER BY create_time DESC, id DESC
SET @existIdxReply = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reply' AND INDEX_NAME = 'idx_reply_message_status_create_time');
SET @sqlReply = IF(@existIdxReply = 0,
    'ALTER TABLE reply ADD INDEX idx_reply_message_status_create_time (message_id, status, create_time ASC, id ASC);',
    'SELECT ''索引 idx_reply_message_status_create_time 已存在，跳过创建'' AS notice;');
PREPARE stmt FROM @sqlReply;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ========== 4. 外键约束（可选，团队确认后再启用） ==========
-- 对应任务单：必要的用户/留言外键或数据完整性检查
-- 约束规则：RESTRICT 模式，禁止级联物理删除，杜绝误操作清除历史数据
-- 启用前必须先完成上方孤儿数据清理
-- ALTER TABLE message ADD CONSTRAINT fk_message_user
--   FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
--
-- ALTER TABLE reply ADD CONSTRAINT fk_reply_message
--   FOREIGN KEY (message_id) REFERENCES message(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
--
-- ALTER TABLE reply ADD CONSTRAINT fk_reply_user
--   FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE RESTRICT ON UPDATE RESTRICT;

-- ========== 业务规则说明（对应任务单要求） ==========
/*
1.  删除机制：采用 status 字段实现逻辑软删除，不物理清除任何历史数据，满足审计留存要求；
    删除留言/回复仅更新对应记录 status=0，公开列表自动过滤 status=0 的数据。
2.  留言删除联动：留言逻辑删除后，其下回复记录在数据库中完整保留；
    公开列表仅查询正常状态的留言，其下回复自然不再对外展示，不做物理级联删除。
3.  排序规则：所有列表统一按 create_time DESC, id DESC 排序；
    id 作为第二排序键，避免同一时间戳下分页数据抖动，保证分页稳定性。
4.  用户删除策略（任务单明确要求）：
    用户被删除时，其发布的留言、回复均保留原始状态，不做级联删除；
    列表页正常展示历史内容，仅关联用户信息缺失时展示用户ID占位，不影响历史数据可读性。
5.  索引设计：完全匹配业务查询路径，解决分页列表 + 批量回复查询的 N+1 性能问题；
    批量回复查询通过一条 message_id IN(...) SQL 完成，替代原逐留言查询模式。
6.  兼容性：本补丁为纯增量变更，不修改原有表结构、不删除原有数据，可安全在线执行。
*/