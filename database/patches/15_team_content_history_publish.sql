-- =====================================================================
-- Patch 15: 团队风采历史内容 PUBLISHED 回填 + 历史文件搬迁
-- 依赖: ganlu.sql → 10_team_core.sql → 11_team_content.sql → 12_team_owner_unique.sql → 本文件
-- 职责: exy v5 Item 3 把 patch 11 不再做的"历史 PENDING→PUBLISHED 回填"独立承接，
--       并处理 Item 1 前置数据迁移（历史 PUBLISHED 图从 images/ 搬回 images_pending/）。
--
-- ⚠️ 执行流程（exy v6 重设计，修复"误公开上线后真实 PENDING"漏洞）：
--   本 patch 采用"运维显式 cutoff + 两步确认"模式，杜绝旧版用 status='PENDING' 推断
--   历史身份、从而误公开上线后真实新 PENDING 内容的问题。
--
--   第一次执行（dry-run，采集快照 + 出清单，不改动业务数据）：
--     SET @patch15_cutoff := '<旧版应用最后一次写入或旧迁移执行的时间点，如 2026-08-06 00:00:00>';
--     SOURCE database/patches/15_team_content_history_publish.sql;
--     -- 脚本输出"将发布 N 条图片 / M 条文字"及 ID 清单后 SIGNAL 中止（仅建快照表，不 UPDATE 业务数据）
--     -- 运维核对数量无误后：
--     SET @patch15_apply := 1;
--     SOURCE database/patches/15_team_content_history_publish.sql;  -- 第二次才执行文件搬迁 + 回填
--     bash database/patches/15_migrate_images_files.sh ...           -- 物理搬迁
--
--   cutoff 语义：只把 createdAt <= @patch15_cutoff 的 PENDING 行视作"历史待回填"，
--   上线后新行 createdAt > cutoff 不进快照、不会被回填公开。依赖 ganlu.sql 中
--   team_page_images.createdAt / team_page_word.createdAt 列。
--
--   章节顺序：1 前置 schema 校验 → 2 cutoff 校验 → 3 快照采集 → 4 dry-run 确认门 →
--             5 文件搬迁清单 + DB 改指 → 6 PUBLISHED 回填 → 7 旧快照清理。
--   dry-run 在第 4 节中止，5/6 不执行；确认运行才执行 5/6。
--
--   仍须在停机窗口执行（停止应用写入团队表，见 patch 11 头注释的维护模式说明）。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 完整前置 schema 校验（exy v6 P2#6）
--    旧版只查 team_media 表 + team_page_images.status 列就认定 patch 11 完整执行，
--    缺 team_page_word.status 等依赖对象时会以 "Unknown column" 非预期中断。
--    现一次性校验本脚本依赖的全部表/列/索引/约束，缺任一项明确 SIGNAL 中止。
-- ---------------------------------------------------------------------
SET @team_media_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_media');
SET @images_team_id_col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_images' AND COLUMN_NAME = 'team_id');
SET @images_status_col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_images' AND COLUMN_NAME = 'status');
SET @images_created_at_col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_images' AND COLUMN_NAME = 'createdAt');
SET @word_status_col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_word' AND COLUMN_NAME = 'status');
SET @word_team_id_col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_word' AND COLUMN_NAME = 'team_id');
SET @word_created_at_col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_word' AND COLUMN_NAME = 'createdAt');
SET @images_idx_team_id := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_images' AND INDEX_NAME = 'idx_team_id');
SET @images_idx_status := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_images' AND INDEX_NAME = 'idx_status');
SET @word_idx_team_id := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_word' AND INDEX_NAME = 'idx_team_id');
SET @word_idx_status := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_word' AND INDEX_NAME = 'idx_status');
SET @team_owner_uk := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team' AND INDEX_NAME = 'uk_team_owner_user');

DELIMITER $$
DROP PROCEDURE IF EXISTS check_patch_prerequisite$$
CREATE PROCEDURE check_patch_prerequisite()
BEGIN
    IF @team_media_exists = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'patch 11 从未执行（team_media 表不存在），请先按顺序执行 10→11→12 后再执行 15';
    END IF;
    IF @images_team_id_col = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'patch 11 部分执行（team_page_images.team_id 列不存在），请补跑 patch 11 后再执行 15';
    END IF;
    IF @images_status_col = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'patch 11 部分执行（team_page_images.status 列不存在），请补跑 patch 11 后再执行 15';
    END IF;
    IF @images_created_at_col = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'team_page_images.createdAt 列不存在（cutoff 回填依赖该列），请核对 ganlu.sql 基线';
    END IF;
    IF @word_team_id_col = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'patch 11 部分执行（team_page_word.team_id 列不存在），请补跑 patch 11 后再执行 15';
    END IF;
    IF @word_status_col = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'patch 11 部分执行（team_page_word.status 列不存在），请补跑 patch 11 后再执行 15';
    END IF;
    IF @word_created_at_col = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'team_page_word.createdAt 列不存在（cutoff 回填依赖该列），请核对 ganlu.sql 基线';
    END IF;
    IF @images_idx_team_id = 0 OR @images_idx_status = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'patch 11 部分执行（team_page_images 缺 idx_team_id/idx_status 索引），请补跑 patch 11';
    END IF;
    IF @word_idx_team_id = 0 OR @word_idx_status = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'patch 11 部分执行（team_page_word 缺 idx_team_id/idx_status 索引），请补跑 patch 11';
    END IF;
    IF @team_owner_uk = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'patch 12 未执行（team.uk_team_owner_user 唯一约束不存在），请先执行 12 后再执行 15';
    END IF;
END$$
CALL check_patch_prerequisite()$$
DROP PROCEDURE IF EXISTS check_patch_prerequisite$$
DELIMITER ;

-- ---------------------------------------------------------------------
-- 2. cutoff 强制校验（exy v6 P1#1）
--    运维必须显式提供 @patch15_cutoff，否则无法区分"旧版遗漏的历史行"与"上线后真实新 PENDING"，
--    盲目回填会把真实待审内容匿名公开。cutoff 缺失即中止。
-- ---------------------------------------------------------------------
DELIMITER $$
DROP PROCEDURE IF EXISTS check_cutoff_provided$$
CREATE PROCEDURE check_cutoff_provided()
BEGIN
    IF @patch15_cutoff IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '未提供 @patch15_cutoff。请先 SET @patch15_cutoff := ''<旧版应用最后一次写入时间点>'' 后重跑（dry-run 会输出待发布清单并中止）';
    END IF;
END$$
CALL check_cutoff_provided()$$
DROP PROCEDURE IF EXISTS check_cutoff_provided$$
DELIMITER ;

-- ---------------------------------------------------------------------
-- 3. 历史 ID 快照采集（带 cutoff 过滤）
--    快照只冻结"createdAt <= cutoff 的历史 PENDING 行 ID"，上线后新行（createdAt > cutoff）
--    不进快照、第 5 节不会回填，彻底杜绝误公开真实待审内容。
--    快照表 _patch15_hist_* 为本 patch 私有，存在性作 guard（恒建，哪怕空表）。
-- ---------------------------------------------------------------------
SET @hist_images_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '_patch15_hist_images');
SET @sql := IF(@hist_images_exists = 0,
  'CREATE TABLE `_patch15_hist_images` (PRIMARY KEY (`id`)) ENGINE = InnoDB AS SELECT `id` FROM `team_page_images` WHERE team_id IS NOT NULL AND status = ''PENDING'' AND createdAt <= @patch15_cutoff',
  'SELECT ''_patch15_hist_images 已存在，跳过采集'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @hist_word_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '_patch15_hist_word');
SET @sql := IF(@hist_word_exists = 0,
  'CREATE TABLE `_patch15_hist_word` (PRIMARY KEY (`id`)) ENGINE = InnoDB AS SELECT `id` FROM `team_page_word` WHERE team_id IS NOT NULL AND status = ''PENDING'' AND createdAt <= @patch15_cutoff',
  'SELECT ''_patch15_hist_word 已存在，跳过采集'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @snapshot_images_count := (SELECT COUNT(*) FROM `_patch15_hist_images`);
SET @snapshot_word_count := (SELECT COUNT(*) FROM `_patch15_hist_word`);

-- ---------------------------------------------------------------------
-- 4. 两步确认（exy v6 P1#1）— dry-run 出清单后中止
--    首次执行（@patch15_apply 未设）：仅输出待发布 ID 清单与数量，SIGNAL 中止，不改动任何业务数据。
--    运维核对后 SET @patch15_apply := 1 重跑，才执行第 5、6 节的文件搬迁与回填。避免误公开。
--    dry-run 阶段除建快照表（审计必需、幂等）外不产生任何业务侧副作用。
-- ---------------------------------------------------------------------
SELECT CONCAT('待发布图片 ', @snapshot_images_count, ' 条 / 待发布文字 ', @snapshot_word_count, ' 条（cutoff=', @patch15_cutoff, ')') AS preview;
SELECT '待发布图片 ID 清单' AS section;
SELECT h.id FROM `_patch15_hist_images` h ORDER BY h.id;
SELECT '待发布文字 ID 清单' AS section;
SELECT h.id FROM `_patch15_hist_word` h ORDER BY h.id;

DELIMITER $$
DROP PROCEDURE IF EXISTS check_apply_confirmation$$
CREATE PROCEDURE check_apply_confirmation()
BEGIN
    IF @patch15_apply IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'dry-run 完成（已输出清单）。核对数量无误后，SET @patch15_apply := 1 重跑本文件执行回填；不回填请勿设该变量';
    END IF;
END$$
CALL check_apply_confirmation()$$
DROP PROCEDURE IF EXISTS check_apply_confirmation$$
DELIMITER ;

-- ---------------------------------------------------------------------
-- 5. 文件搬迁前置数据迁移（exy v6 P1#2）— 持久化迁移清单表
--    仅在确认运行（@patch15_apply = 1）阶段执行。旧版只用 imageUrl LIKE 'images/%' 改写，
--    漏掉 Windows 历史反斜杠路径（images\xxx.jpg，FileStorageUtil.loadFile 仍兼容这种形态）。
--    漏改的行物理文件滞留 /images/** 静态公开目录，驳回/归档后旧 URL 仍可绕过 serveImage
--    状态校验直接读取。现引入持久化清单表 _patch15_image_migration，统一归一化两种前缀
--    （images/ 与 images\），配套搬迁脚本只消费该清单，精确搬迁。
--
--    幂等：清单表 CREATE IF NOT EXISTS；INSERT 用 LEFT JOIN ... WHERE m.id IS NULL 跳过已采集行。
--    顺序：本 SQL 先建清单并改 DB imageUrl→images_pending/，运维再跑 15_migrate_images_files.sh
--    把物理文件搬过去。中途崩溃：已改 DB 的行 serveImage 读 images_pending/，文件没搬到的 404
--    直到脚本补搬，不丢数据。images/ 与课件缩略图混存，不能整目录 mv——只搬清单内引用的文件。
--    team_page_word 不涉及图片文件，无需搬迁。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `_patch15_image_migration` (
  `id` int(0) NOT NULL,
  `old_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `new_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci;

-- 5.1 正斜杠前缀 images/xxx → 采集到清单（SUBSTRING 跳过 7 字符前缀取 basename）
INSERT INTO `_patch15_image_migration` (id, old_url, new_url)
  SELECT img.id, img.imageUrl, CONCAT('images_pending/', SUBSTRING(img.imageUrl, 8))
    FROM `team_page_images` img
    LEFT JOIN `_patch15_image_migration` m ON m.id = img.id
   WHERE m.id IS NULL AND LEFT(img.imageUrl, 7) = 'images/';

-- 5.2 反斜杠前缀 images\xxx（Windows 历史路径）。
--    用 LEFT(...)='images' AND SUBSTRING(...) = CHAR(92) 匹配，避免反斜杠在 SQL 字符串字面量
--    中的转义歧义（默认 sql_mode 下 '\' 会被解释为转义引号而非反斜杠字符）。CHAR(92) = 反斜杠。
INSERT INTO `_patch15_image_migration` (id, old_url, new_url)
  SELECT img.id, img.imageUrl, CONCAT('images_pending/', SUBSTRING(img.imageUrl, 8))
    FROM `team_page_images` img
    LEFT JOIN `_patch15_image_migration` m ON m.id = img.id
   WHERE m.id IS NULL
     AND LEFT(img.imageUrl, 6) = 'images'
     AND SUBSTRING(img.imageUrl, 7, 1) = CHAR(92);

-- 5.3 按清单把 DB imageUrl 改指 images_pending/（幂等：已是新值的行 old_url 不匹配）
UPDATE `team_page_images` img
  JOIN `_patch15_image_migration` m ON m.id = img.id
   SET img.imageUrl = m.new_url
 WHERE img.imageUrl = m.old_url;

-- ---------------------------------------------------------------------
-- 6. 历史内容回填 PUBLISHED — 基于第 3 节快照
--    只回填快照内、仍 PENDING 的行。天然幂等（已 PUBLISHED 的 WHERE status='PENDING' 不命中）。
--    无需回填（快照为空）时这两条 UPDATE JOIN 空表，0 行变更。
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
-- 7. 清理旧版残留（2976c24 版 patch 11 跑过的库上有 _patch11_hist_*）
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

SELECT 'patch 15 历史回填 + 文件搬迁清单完成（请执行 15_migrate_images_files.sh 完成物理搬迁）' AS migration_result;
