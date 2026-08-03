-- =====================================================================
-- Patch 11: 团队风采内容管理 — team_media 表 + 旧表加列 + 数据回填
-- 依赖: ganlu.sql + Patch 10（Patch 10 已把 team_page.userId 迁移为 team_page.team_id）
-- 执行顺序: ganlu.sql → 10_team_core.sql → 本文件
--
-- 幂等性说明：
--   - DDL 段（CREATE TABLE / ALTER TABLE / ADD INDEX）通过 INFORMATION_SCHEMA
--     条件判断，可安全重入。
--   - DML 段（数据回填）是一次性历史数据迁移，仅在本次新建 status 列时执行，
--     重跑时自动跳过，避免上线后把真正等待审核的 PENDING 内容批量公开。
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 记录本次执行前 status 列是否已存在，用于第 6 节判断是否需要历史回填
SET @images_status_existed_before := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_images' AND COLUMN_NAME = 'status');
SET @word_status_existed_before := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_word' AND COLUMN_NAME = 'status');

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
-- 6. 历史内容回填 PUBLISHED — 一次性操作
--    仅在本次确实新建 status 列时执行（@*_status_existed_before = 0），
--    重跑时 status 列已存在，直接跳过，避免上线后把真正等待审核的 PENDING 内容批量公开。
--    新建 status 列时，历史记录因 ADD COLUMN DEFAULT 'PENDING' 落到 PENDING，
--    此处统一调整为 PUBLISHED，表示迁移前已存在的老数据视为已发布。
-- ---------------------------------------------------------------------
SET @sql := IF(@images_status_existed_before = 0,
  'UPDATE team_page_images SET status = ''PUBLISHED'' WHERE team_id IS NOT NULL AND status = ''PENDING''',
  'SELECT ''team_page_images.status 列已存在，历史回填跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@word_status_existed_before = 0,
  'UPDATE team_page_word SET status = ''PUBLISHED'' WHERE team_id IS NOT NULL AND status = ''PENDING''',
  'SELECT ''team_page_word.status 列已存在，历史回填跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
