-- =====================================================================
-- Patch 15: 团队风采历史内容 PUBLISHED 回填 + 历史文件搬迁
-- 依赖: ganlu.sql → 10_team_core.sql → 11_team_content.sql → 12_team_owner_unique.sql → 本文件
-- 职责: exy v5 Item 3 把 patch 11 不再做的"历史 PENDING→PUBLISHED 回填"独立承接，
--       并处理 Item 1 前置数据迁移（历史 PUBLISHED 图从 images/ 搬回 images_pending/）。
--
-- 三态识别（exy v5 要求）：
--   - team_media 不存在 → patch 11 从未执行 → SIGNAL 中止
--   - team_page_images.status 列不存在 → patch 11 部分执行 → SIGNAL 中止
--   - 两者都在 → patch 11 已执行，继续
--
-- ⚠️ 执行时机（重要）：
--   首次执行和重跑都必须在应用上线前（停机窗口）——上线后执行会把应用新增的
--   PENDING 内容批量公开（完成度判断无法区分"旧版遗漏的历史行"与"上线后新行"）。
--   执行期间停止应用写入团队表（见 patch 11 头注释的维护模式说明）。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 三态识别
-- ---------------------------------------------------------------------
SET @team_media_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_media');
SET @images_status_col_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_images' AND COLUMN_NAME = 'status');

DELIMITER $$
DROP PROCEDURE IF EXISTS check_patch11_prerequisite$$
CREATE PROCEDURE check_patch11_prerequisite()
BEGIN
    IF @team_media_exists = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'patch 11 从未执行（team_media 表不存在），请先按顺序执行 10→11→12 后再执行 15';
    END IF;
    IF @images_status_col_exists = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'patch 11 部分执行（team_page_images.status 列不存在），请补跑 patch 11 后再执行 15';
    END IF;
END$$
CALL check_patch11_prerequisite()$$
DROP PROCEDURE IF EXISTS check_patch11_prerequisite$$
DELIMITER ;

-- ---------------------------------------------------------------------
-- 2. 历史回填完成度判断
--    查 team_id IS NOT NULL AND status='PENDING' 的行数：
--    - 两表都为 0 → 历史回填已完成（旧版 d9873b1 公开过，或无历史数据）→ 无需回填
--    - 有行 → 需要历史回填
--    无论哪种，第 3 节都会建快照表（无回填需求时为空表），第 5 节 JOIN 空表天然 0 行变更。
-- ---------------------------------------------------------------------
SET @pending_images_count := (SELECT COUNT(*) FROM team_page_images
  WHERE team_id IS NOT NULL AND status = 'PENDING');
SET @pending_word_count := (SELECT COUNT(*) FROM team_page_word
  WHERE team_id IS NOT NULL AND status = 'PENDING');
SET @needs_history_publish := IF(@pending_images_count > 0 OR @pending_word_count > 0, 1, 0);

-- ---------------------------------------------------------------------
-- 3. 历史 ID 快照采集（快照表不存在时恒建；无历史需回填时为空表）
--    快照表 _patch15_hist_* 为本 patch 私有，存在性作 guard（不依赖 team_media）。
--    快照冻结"需要回填的历史行 ID 清单"，之后只回填快照内 ID，保证幂等 + 不误伤上线后新行。
--    ⚠️ 必须恒建（哪怕空表）：第 5 节的 UPDATE...JOIN 引用该表，表不存在会报错中止整个 patch。
-- ---------------------------------------------------------------------
SET @hist_images_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '_patch15_hist_images');
SET @sql := IF(@hist_images_exists = 0,
  'CREATE TABLE `_patch15_hist_images` (PRIMARY KEY (`id`)) ENGINE = InnoDB AS SELECT `id` FROM `team_page_images` WHERE team_id IS NOT NULL AND status = ''PENDING''',
  'SELECT ''_patch15_hist_images 已存在，跳过采集'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @hist_word_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '_patch15_hist_word');
SET @sql := IF(@hist_word_exists = 0,
  'CREATE TABLE `_patch15_hist_word` (PRIMARY KEY (`id`)) ENGINE = InnoDB AS SELECT `id` FROM `team_page_word` WHERE team_id IS NOT NULL AND status = ''PENDING''',
  'SELECT ''_patch15_hist_word 已存在，跳过采集'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 快照缺失保护：需要回填但快照表不存在且无法重建 → SIGNAL
DELIMITER $$
DROP PROCEDURE IF EXISTS check_snapshot_available$$
CREATE PROCEDURE check_snapshot_available()
BEGIN
    IF @needs_history_publish = 1 THEN
        IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '_patch15_hist_images') = 0
           OR (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '_patch15_hist_word') = 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = '需要历史回填但快照表不存在且无法重建，请人工核查 _patch15_hist_* 表后重跑';
        END IF;
    END IF;
END$$
CALL check_snapshot_available()$$
DROP PROCEDURE IF EXISTS check_snapshot_available$$
DELIMITER ;

-- ---------------------------------------------------------------------
-- 4. 历史文件搬迁（Item 1 前置数据迁移）— DB 先标记
--    把已 PUBLISHED 且 imageUrl 仍指 images/ 的历史行，DB 层先把 imageUrl 改指 images_pending/。
--    物理文件搬迁由配套脚本 15_migrate_images_files.sh 完成（幂等可重入，按新 imageUrl 清单逐文件 mv）。
--    顺序约束：本 SQL 先改 DB（imageUrl→images_pending/），运维再跑搬迁脚本把文件实际搬过去。
--    中途崩溃：已改 DB 的行 serveImage 读 images_pending/，文件没搬到的会 404 直到脚本补搬，不丢数据。
--
--    注意：images/ 与课件缩略图混存，不能整目录 mv——只搬 team_page_images 表引用的文件。
--    team_page_word 不涉及图片文件，无需搬迁。
-- ---------------------------------------------------------------------
UPDATE team_page_images
   SET imageUrl = REPLACE(imageUrl, 'images/', 'images_pending/')
 WHERE imageUrl LIKE 'images/%';

-- ---------------------------------------------------------------------
-- 5. 历史内容回填 PUBLISHED — 基于第 3 节快照
--    只回填快照内、仍 PENDING 的行。天然幂等（已 PUBLISHED 的 WHERE status='PENDING' 不命中）。
--    无需回填（@needs_history_publish=0）时快照表为空表，这两条 UPDATE JOIN 空表，0 行变更。
-- ---------------------------------------------------------------------
UPDATE team_page_images img
  JOIN `_patch15_hist_images` h ON h.id = img.id
   SET img.status = 'PUBLISHED'
 WHERE img.status = 'PENDING' AND img.team_id IS NOT NULL;

UPDATE team_page_word wrd
  JOIN `_patch15_hist_word` h ON h.id = wrd.id
   SET wrd.status = 'PUBLISHED'
 WHERE wrd.status = 'PENDING' AND wrd.team_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- 6. 清理旧版残留（2976c24 版 patch 11 跑过的库上有 _patch11_hist_*）
--    exy v5 修正 2：不 DROP（防误删快照数据），改 RENAME 备份，保留审计依据。
--    若表不存在 RENAME 会报错，故用 INFORMATION_SCHEMA 条件判断。
-- ---------------------------------------------------------------------
SET @old_snap_images := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '_patch11_hist_images');
SET @sql := IF(@old_snap_images > 0,
  'RENAME TABLE `_patch11_hist_images` TO `_patch11_hist_images_deprecated`',
  'SELECT ''无 _patch11_hist_images 残留，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @old_snap_word := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '_patch11_hist_word');
SET @sql := IF(@old_snap_word > 0,
  'RENAME TABLE `_patch11_hist_word` TO `_patch11_hist_word_deprecated`',
  'SELECT ''无 _patch11_hist_word 残留，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT 'patch 15 历史回填 + 文件搬迁标记完成' AS migration_result;
