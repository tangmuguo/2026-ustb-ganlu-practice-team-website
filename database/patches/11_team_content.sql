-- =====================================================================
-- Patch 11: 团队风采内容管理 — team_media 表 + 旧表加列 + 数据回填
-- 依赖: ganlu.sql（team_page_images / team_page_word 表已存在）
-- 执行顺序: ganlu.sql → 本文件
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------
-- 1. CREATE TABLE team_media — 视频/附件表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `team_media`;
CREATE TABLE `team_media`  (
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
-- ---------------------------------------------------------------------
ALTER TABLE `team_page_images`
  ADD COLUMN `team_id` int(0) NULL DEFAULT NULL COMMENT '所属团队ID' AFTER `id`,
  ADD COLUMN `status` enum('PENDING','PUBLISHED','REJECTED','ARCHIVED') NULL DEFAULT 'PENDING' COMMENT '审核状态' AFTER `team_id`,
  ADD COLUMN `reject_reason` varchar(512) NULL DEFAULT NULL COMMENT '驳回原因' AFTER `status`,
  ADD COLUMN `log_date` date NULL DEFAULT NULL COMMENT '拍摄日期(用户可选)' AFTER `reject_reason`;

ALTER TABLE `team_page_images`
  ADD INDEX `idx_team_id`(`team_id`),
  ADD INDEX `idx_status`(`status`);

-- ---------------------------------------------------------------------
-- 3. ALTER TABLE team_page_word — 同上（注意旧列名小写 userid）
-- ---------------------------------------------------------------------
ALTER TABLE `team_page_word`
  ADD COLUMN `team_id` int(0) NULL DEFAULT NULL COMMENT '所属团队ID' AFTER `id`,
  ADD COLUMN `status` enum('PENDING','PUBLISHED','REJECTED','ARCHIVED') NULL DEFAULT 'PENDING' COMMENT '审核状态' AFTER `team_id`,
  ADD COLUMN `reject_reason` varchar(512) NULL DEFAULT NULL COMMENT '驳回原因' AFTER `status`,
  ADD COLUMN `log_date` date NULL DEFAULT NULL COMMENT '日志日期/获奖日期(用户可选)' AFTER `reject_reason`;

ALTER TABLE `team_page_word`
  ADD INDEX `idx_team_id`(`team_id`),
  ADD INDEX `idx_status`(`status`);

-- ---------------------------------------------------------------------
-- 4. 数据回填（主回填 + 兜底）
-- ---------------------------------------------------------------------
-- team_page_images: 主回填 — 通过 pageId 关联 team_page.userId
UPDATE team_page_images img
  JOIN team_page page ON img.pageId = page.id
SET img.team_id = page.userId
WHERE img.pageId IS NOT NULL;

-- team_page_images: 兜底回填 — pageId 为 NULL 但旧 userId 有值
UPDATE team_page_images
SET team_id = userId
WHERE team_id IS NULL AND userId IS NOT NULL;

-- team_page_word: 主回填（注意小写 userid）
UPDATE team_page_word wrd
  JOIN team_page page ON wrd.pageId = page.id
SET wrd.team_id = page.userId
WHERE wrd.pageId IS NOT NULL;

-- team_page_word: 兜底回填（注意小写 userid）
UPDATE team_page_word
SET team_id = userid
WHERE team_id IS NULL AND userid IS NOT NULL;

-- ---------------------------------------------------------------------
-- 5. 最终检查 — 仍无法映射的记录（userId 和 pageId 都为 NULL）
-- ---------------------------------------------------------------------
SELECT 'team_page_images_orphan' AS scope, COUNT(*) AS cnt
FROM team_page_images WHERE team_id IS NULL
UNION ALL
SELECT 'team_page_word_orphan', COUNT(*)
FROM team_page_word WHERE team_id IS NULL;

SET FOREIGN_KEY_CHECKS = 1;
