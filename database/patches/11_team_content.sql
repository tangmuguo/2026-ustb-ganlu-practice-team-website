-- =====================================================================
-- Patch 11: 团队风采内容管理 — team_media 表 + 旧表加列 + 数据回填
-- 依赖: ganlu.sql + Patch 10（Patch 10 已把 team_page.userId 迁移为 team_page.team_id）
-- 执行顺序: ganlu.sql → 10_team_core.sql → 本文件 → 12_team_owner_unique.sql → 15_team_content_history_publish.sql
--
-- 职责（exy v5 回退后）：
--   本 patch 只做 schema 演进 + team_id 回填 + orphan 校验，**不做历史 PENDING→PUBLISHED 公开**。
--   历史内容的 PUBLISHED 回填由独立的 patch 15 负责（三态识别 + 快照 + 幂等回填）。
--   这样 patch 11 对"从未执行"和"已执行过旧版（d9873b1/2976c24）"的库都不崩溃。
--
-- 幂等性说明：
--   - DDL 段（CREATE TABLE / ALTER TABLE / ADD INDEX）通过 INFORMATION_SCHEMA
--     条件判断，可安全重入。
--   - team_id 回填（第 4/4b 节）有 team_id IS NULL 条件，天然幂等。
--
-- ⚠️ 维护模式（Item 4 exy v5）：执行 patch 10/11/12/15 期间必须停止应用写入
--    team / team_page / team_page_images / team_page_word 表，避免并发写入破坏
--    owner 预检（3.5 节）与唯一约束（patch 12）之间的检查窗口。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. CREATE TABLE team_media — 视频/附件表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `team_media` (
  `id` int(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '原始文件名',
  `relative_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '存储相对路径（uploadRoot 下）',
  `mime_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'MIME 类型',
  `file_size` bigint(0) NULL DEFAULT NULL COMMENT '文件大小(字节)',
  `uploader_id` int(0) NULL DEFAULT NULL COMMENT '上传者用户ID',
  `team_id` int(0) NULL DEFAULT NULL COMMENT '所属团队ID',
  `related_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '关联父内容类型: IMAGE/WORD',
  `related_id` int(0) NULL DEFAULT NULL COMMENT '关联父内容ID',
  `status` enum('PENDING','PUBLISHED','REJECTED','ARCHIVED') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PENDING' COMMENT '审核状态',
  `reject_reason` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '驳回原因',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_team_id`(`team_id`) USING BTREE,
  INDEX `idx_uploader_id`(`uploader_id`) USING BTREE,
  INDEX `idx_related`(`related_type`, `related_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '团队风采视频/附件表' ROW_FORMAT = Dynamic;

-- ---------------------------------------------------------------------
-- 2. ALTER TABLE team_page_images — 新增 team_id / status / reject_reason / log_date
--    使用 INFORMATION_SCHEMA 条件判断，保证 DDL 可安全重入
-- ---------------------------------------------------------------------

-- 2.1 team_id 列
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_images' AND COLUMN_NAME = 'team_id');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `team_page_images` ADD COLUMN `team_id` int(0) NULL DEFAULT NULL COMMENT ''所属团队ID'' AFTER `id`',
  'SELECT ''team_page_images.team_id 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.2 status 列
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_images' AND COLUMN_NAME = 'status');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `team_page_images` ADD COLUMN `status` enum(''PENDING'',''PUBLISHED'',''REJECTED'',''ARCHIVED'') NULL DEFAULT ''PENDING'' COMMENT ''审核状态'' AFTER `team_id`',
  'SELECT ''team_page_images.status 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.3 reject_reason 列
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_images' AND COLUMN_NAME = 'reject_reason');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `team_page_images` ADD COLUMN `reject_reason` varchar(512) NULL DEFAULT NULL COMMENT ''驳回原因'' AFTER `status`',
  'SELECT ''team_page_images.reject_reason 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.4 log_date 列
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_images' AND COLUMN_NAME = 'log_date');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `team_page_images` ADD COLUMN `log_date` date NULL DEFAULT NULL COMMENT ''拍摄日期(用户可选)'' AFTER `reject_reason`',
  'SELECT ''team_page_images.log_date 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.5 索引 idx_team_id
SET @idx_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_images' AND INDEX_NAME = 'idx_team_id');
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE `team_page_images` ADD INDEX `idx_team_id`(`team_id`)',
  'SELECT ''team_page_images.idx_team_id 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.6 索引 idx_status
SET @idx_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_images' AND INDEX_NAME = 'idx_status');
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE `team_page_images` ADD INDEX `idx_status`(`status`)',
  'SELECT ''team_page_images.idx_status 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. ALTER TABLE team_page_word — 同上（注意旧列名小写 userid）
-- ---------------------------------------------------------------------

-- 3.1 team_id 列
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_word' AND COLUMN_NAME = 'team_id');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `team_page_word` ADD COLUMN `team_id` int(0) NULL DEFAULT NULL COMMENT ''所属团队ID'' AFTER `id`',
  'SELECT ''team_page_word.team_id 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3.2 status 列
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_word' AND COLUMN_NAME = 'status');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `team_page_word` ADD COLUMN `status` enum(''PENDING'',''PUBLISHED'',''REJECTED'',''ARCHIVED'') NULL DEFAULT ''PENDING'' COMMENT ''审核状态'' AFTER `team_id`',
  'SELECT ''team_page_word.status 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3.3 reject_reason 列
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_word' AND COLUMN_NAME = 'reject_reason');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `team_page_word` ADD COLUMN `reject_reason` varchar(512) NULL DEFAULT NULL COMMENT ''驳回原因'' AFTER `status`',
  'SELECT ''team_page_word.reject_reason 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3.4 log_date 列
SET @col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_word' AND COLUMN_NAME = 'log_date');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `team_page_word` ADD COLUMN `log_date` date NULL DEFAULT NULL COMMENT ''日志日期/获奖日期(用户可选)'' AFTER `reject_reason`',
  'SELECT ''team_page_word.log_date 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3.5 索引 idx_team_id
SET @idx_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_word' AND INDEX_NAME = 'idx_team_id');
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE `team_page_word` ADD INDEX `idx_team_id`(`team_id`)',
  'SELECT ''team_page_word.idx_team_id 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3.6 索引 idx_status
SET @idx_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_word' AND INDEX_NAME = 'idx_status');
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE `team_page_word` ADD INDEX `idx_status`(`status`)',
  'SELECT ''team_page_word.idx_status 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3.5 owner 正向重复预检 — 必须在任何内容回填之前执行
--     Patch 10 只校验"一个 team 多 user"，没校验"一个 user 多 team"。
--     若同一 owner_user_id 绑了多个 team，第 4b 节兜底回填的 JOIN team t ON
--     t.owner_user_id = img.userId 会多匹配并任选其一写入 team_id，造成历史内容
--     错误归属且无法通过重跑修复（team_id 已非 NULL，4b 的 IS NULL 条件不再命中）。
--     因此必须在回填前 SIGNAL 中止，要求人工合并/解绑重复 owner。
--     （Patch 12 才补 UNIQUE(owner_user_id) 约束，此预检把检测时机提前。）
--     竞态兜底（exy v5 P2-4）：即使预检后并发写入了重复 owner，第 4b 节的
--     无歧义 JOIN 也不会错归属——多匹配行留在 team_id IS NULL，由第 5 节 orphan
--     校验 SIGNAL 响亮中止；patch 12 的 ADD UNIQUE 在重复数据上同样响亮失败。
--     最坏情况全部是"可发现、需人工修"，不存在静默数据污染。
-- ---------------------------------------------------------------------
DELIMITER $$
DROP PROCEDURE IF EXISTS check_team_owner_unique_before_backfill$$
CREATE PROCEDURE check_team_owner_unique_before_backfill()
BEGIN
    DECLARE duplicate_owner INT DEFAULT 0;
    SELECT COUNT(1) INTO duplicate_owner
      FROM (
          SELECT owner_user_id, COUNT(1) AS c
            FROM team
           WHERE owner_user_id IS NOT NULL
           GROUP BY owner_user_id
          HAVING c > 1
      ) dup;
    IF duplicate_owner > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '存在同一负责人账号(owner_user_id)绑定多个小队，请先合并/解绑后再执行 11_team_content.sql';
    END IF;
END$$
CALL check_team_owner_unique_before_backfill()$$
DROP PROCEDURE IF EXISTS check_team_owner_unique_before_backfill$$
DELIMITER ;

-- ---------------------------------------------------------------------
-- 4. 数据回填 — 主路径：通过 pageId 关联 team_page.team_id
--    Patch 10 已把 team_page.userId 迁移为真实 team_page.team_id（team.id），
--    因此这里直接使用 page.team_id，确保内容表 team_id 与 team.id 语义一致。
-- ---------------------------------------------------------------------
UPDATE team_page_images img
  JOIN team_page page ON img.pageId = page.id
SET img.team_id = page.team_id
WHERE img.pageId IS NOT NULL AND page.team_id IS NOT NULL;

UPDATE team_page_word wrd
  JOIN team_page page ON wrd.pageId = page.id
SET wrd.team_id = page.team_id
WHERE wrd.pageId IS NOT NULL AND page.team_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- 4b. 数据回填 — 兜底路径：pageId 为空的历史记录
--     旧前端 FengCaiAction 的 /addImage、/addWord 只设 userId（团队账号 user.id），
--     从不设 pageId，因此这批记录无法命中主路径 JOIN。
--     通过 team.owner_user_id = 内容表.userId/userid 兜底映射到 team.id
--     （Patch 10 已建立 user.id → team.owner_user_id 的 owner 关系）。
--
--     无歧义化（exy v5 P2-4 竞态窗口收口）：JOIN 限定 owner 唯一（COUNT=1）的账号。
--     3.5 预检通过后若并发写入了重复 owner，多匹配的内容行 JOIN 不命中、
--     保持 team_id IS NULL，由第 5 节 orphan 校验 SIGNAL 响亮中止——
--     杜绝"UPDATE JOIN 任选其一"导致的静默错归属（错归属后 team_id 已非 NULL，
--     无法靠重跑修复）。唯一 owner 的正常路径语义与原来完全一致。
-- ---------------------------------------------------------------------
UPDATE team_page_images img
  JOIN (SELECT owner_user_id FROM team
         WHERE owner_user_id IS NOT NULL
         GROUP BY owner_user_id HAVING COUNT(1) = 1) u ON u.owner_user_id = img.userId
  JOIN team t ON t.owner_user_id = img.userId
SET img.team_id = t.id
WHERE img.pageId IS NULL
  AND img.team_id IS NULL
  AND img.userId IS NOT NULL;

UPDATE team_page_word wrd
  JOIN (SELECT owner_user_id FROM team
         WHERE owner_user_id IS NOT NULL
         GROUP BY owner_user_id HAVING COUNT(1) = 1) u ON u.owner_user_id = wrd.userid
  JOIN team t ON t.owner_user_id = wrd.userid
SET wrd.team_id = t.id
WHERE wrd.pageId IS NULL
  AND wrd.team_id IS NULL
  AND wrd.userid IS NOT NULL;

-- ---------------------------------------------------------------------
-- 5. 最终校验 — 主路径 + 兜底路径后仍存在 team_id IS NULL 的记录
--    说明存在无法映射的脏数据（owner 已删 / team 已归档未绑新 owner 等），
--    中止迁移，要求人工澄清，避免内容静默消失。
--    （参照 Patch 10 的 SIGNAL 严谨风格，取代原先仅 SELECT 的软提示。）
-- ---------------------------------------------------------------------
DELIMITER $$
DROP PROCEDURE IF EXISTS check_team_content_orphans$$
CREATE PROCEDURE check_team_content_orphans()
BEGIN
    DECLARE orphan_images INT DEFAULT 0;
    DECLARE orphan_words INT DEFAULT 0;
    SELECT COUNT(*) INTO orphan_images
      FROM team_page_images WHERE team_id IS NULL;
    SELECT COUNT(*) INTO orphan_words
      FROM team_page_word WHERE team_id IS NULL;
    IF orphan_images > 0 OR orphan_words > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '存在 team_id IS NULL 的历史内容记录，无法映射到任何小队，请根据备份手工澄清后再执行';
    END IF;
END$$
CALL check_team_content_orphans()$$
DROP PROCEDURE IF EXISTS check_team_content_orphans$$
DELIMITER ;

-- ---------------------------------------------------------------------
-- 6. 历史内容回填 PUBLISHED — 已挪到 patch 15
--    exy v5 Item 3：历史回填的幂等性/兼容性问题（快照方案对旧库崩溃、orphan 重跑永久 PENDING）
--    已由独立的 15_team_content_history_publish.sql 解决。本 patch 不再做 PENDING→PUBLISHED 公开，
--    避免对已执行过旧版 patch 11 的库造成不兼容。
--    patch 15 会做三态识别（从未执行/部分执行/已完整执行）+ 快照 + 幂等回填。
-- ---------------------------------------------------------------------
