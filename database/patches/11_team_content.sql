-- =====================================================================
-- Patch 11: 团队风采内容管理 — team_media 表 + 旧表加列 + 数据回填
-- 依赖: ganlu.sql + Patch 10（Patch 10 已把 team_page.userId 迁移为 team_page.team_id）
-- 执行顺序: ganlu.sql → 10_team_core.sql → 本文件
-- 本 patch 全程幂等（CREATE TABLE IF NOT EXISTS + INFORMATION_SCHEMA 列/索引存在性判断）
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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
--    使用 INFORMATION_SCHEMA 条件判断，保证脚本可安全重入
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
-- 4. 数据回填 — 通过 pageId 关联 team_page.team_id
--    Patch 10 已把 team_page.userId 迁移为真实 team_page.team_id（team.id），
--    因此这里直接使用 page.team_id，确保内容表 team_id 与 team.id 语义一致。
--    不再通过内容表自身的旧 userId/userid 兜底回填（那存放的是 user.id，
--    会造成同一列 team_id 存在两种 ID 语义）；无法映射的记录由第 5 节输出。
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
-- 5. 最终检查 — 仍无法映射的记录（pageId 为 NULL 或 team_page 未映射）
--    输出待人工处理清单，迁移不会因此中止。
-- ---------------------------------------------------------------------
SELECT 'team_page_images_orphan' AS scope, COUNT(*) AS cnt
FROM team_page_images WHERE team_id IS NULL
UNION ALL
SELECT 'team_page_word_orphan', COUNT(*)
FROM team_page_word WHERE team_id IS NULL;

-- ---------------------------------------------------------------------
-- 6. 历史内容回填 PUBLISHED — 所有成功映射 team_id 的记录视为已发布内容
--    （主回填 + 兜底回填后的全部记录，避免兜底数据因 pageId 为空被隐藏）
-- ---------------------------------------------------------------------
UPDATE team_page_images SET status = 'PUBLISHED' WHERE team_id IS NOT NULL AND status = 'PENDING';
UPDATE team_page_word SET status = 'PUBLISHED' WHERE team_id IS NOT NULL AND status = 'PENDING';

SET FOREIGN_KEY_CHECKS = 1;
