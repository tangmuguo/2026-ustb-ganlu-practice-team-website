-- =====================================================================
-- Patch 11: 团队风采内容管理 — team_media 表 + 旧表加列 + 数据回填
-- 依赖: ganlu.sql + Patch 10（Patch 10 已把 team_page.userId 迁移为 team_page.team_id）
-- 执行顺序: ganlu.sql → 10_team_core.sql → 本文件
--
-- 幂等性说明：
--   - DDL 段（CREATE TABLE / ALTER TABLE / ADD INDEX）通过 INFORMATION_SCHEMA
--     条件判断，可安全重入。
--   - DML 历史回填（第 6 节）基于历史 ID 快照表（_patch11_hist_images / _patch11_hist_word）
--     JOIN 完成。快照在脚本最顶部、任何 DDL 之前一次性采集（表不存在才 CREATE AS SELECT），
--     保证失败重跑时仍能按原历史 ID 清单完成回填，且上线后新插入的 PENDING 内容
--     不在快照中、永不被误公开（满足 exy v4 "完成且只完成历史回填" 的要求）。
--   - ⚠️ 快照表 _patch11_hist_* 为常驻表，禁止 DROP——DROP 后重跑会重新采集
--     （含上线后新行），破坏"只完成历史回填"语义。
--   - ⚠️ 本脚本仅一次性手工执行；上线后不建议重跑（第 5 节 orphan 校验会挡住
--     上线后应用产生的 team_id IS NULL 新行）。
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------
-- 0. 历史 ID 快照采集 — 必须在任何 DDL 之前执行
--    仅首次执行时采集（快照表不存在才 CREATE AS SELECT）；之后无论失败重跑多少次，
--    只回填快照里的历史行。上线后新插入的 PENDING 行不在快照中，永不被误公开。
--    快照表为常驻表，禁止 DROP（见头注释）。
--
--    额外守卫（F1 review）：快照采集还要满足 team_media 表不存在。team_media 是本 patch
--    第 1 节创建的，它存在 = patch 已完整跑过一次（含快照）。这样即使 _patch11_hist_*
--    被误删（当临时表清理、逻辑备份遗漏），只要 team_media 还在，就不会重建快照——
--    堵住"快照丢失 + status 列已存在 → 重跑重建快照（含上线后 PENDING）→ 批量误公开"。
--    （team_media 不存在 = patch 从未跑完整，此时即使重建快照也只含迁移前历史行，安全。）
-- ---------------------------------------------------------------------
SET @team_media_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_media');
SET @snap_images_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '_patch11_hist_images');
SET @sql := IF(@snap_images_exists = 0 AND @team_media_exists = 0,
  'CREATE TABLE `_patch11_hist_images` (PRIMARY KEY (`id`)) ENGINE = InnoDB AS SELECT `id` FROM `team_page_images`',
  'SELECT ''_patch11_hist_images 已存在或 patch 已完整执行过，跳过采集'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @snap_word_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '_patch11_hist_word');
SET @sql := IF(@snap_word_exists = 0 AND @team_media_exists = 0,
  'CREATE TABLE `_patch11_hist_word` (PRIMARY KEY (`id`)) ENGINE = InnoDB AS SELECT `id` FROM `team_page_word`',
  'SELECT ''_patch11_hist_word 已存在或 patch 已完整执行过，跳过采集'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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
--     Patch 12 加 UNIQUE(owner_user_id) 后此映射严格 1:1，不会歧义。
-- ---------------------------------------------------------------------
UPDATE team_page_images img
  JOIN team t ON t.owner_user_id = img.userId
SET img.team_id = t.id
WHERE img.pageId IS NULL
  AND img.team_id IS NULL
  AND img.userId IS NOT NULL;

UPDATE team_page_word wrd
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
-- 6. 历史内容回填 PUBLISHED — 基于第 0 节的历史 ID 快照
--    只回填快照表（_patch11_hist_*）里记录的历史行，且仅当它们仍处于 PENDING。
--    这样满足 exy v4 "完成且只完成历史回填" 的要求：
--      - 失败重跑：快照在脚本顶部采集（orphan 校验之前），首次失败重跑后历史 ID 齐全，
--        回填照常完成。
--      - 上线后重跑：新插入的 PENDING 内容不在快照中，永不被误公开。
--      - 已回填的行 status 已是 PUBLISHED，WHERE status='PENDING' 不再命中，天然幂等。
-- ---------------------------------------------------------------------
UPDATE team_page_images img
  JOIN `_patch11_hist_images` h ON h.id = img.id
   SET img.status = 'PUBLISHED'
 WHERE img.status = 'PENDING' AND img.team_id IS NOT NULL;

UPDATE team_page_word wrd
  JOIN `_patch11_hist_word` h ON h.id = wrd.id
   SET wrd.status = 'PUBLISHED'
 WHERE wrd.status = 'PENDING' AND wrd.team_id IS NOT NULL;

SET FOREIGN_KEY_CHECKS = 1;
