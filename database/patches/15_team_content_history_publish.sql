-- =====================================================================
-- Patch 15: 团队风采历史内容 PUBLISHED 回填 + 历史文件搬迁
-- 依赖: ganlu.sql → 10_team_core.sql → 11_team_content.sql → 12_team_owner_unique.sql → 本文件
-- 职责: exy v5 Item 3 把 patch 11 不再做的"历史 PENDING→PUBLISHED 回填"独立承接，
--       并处理 Item 1 前置数据迁移（历史 PUBLISHED 图从 images/ 搬回 images_pending/）。
--
-- ⚠️ 执行流程（exy v7 收口，修复 v6 cutoff 双确认的可绕过点）：
--   本 patch 采用"运维显式 cutoff + 持久化状态机 + 单过程门控"模式，杜绝四类问题：
--     1. 错误 cutoff 的旧快照被复用（快照绑定 cutoff，apply 时强校验一致性，不一致即中止）
--     2. @patch15_apply 任意非空值放行（严格 COALESCE(apply,0)=1，apply=0 走 dry-run）
--     3. mysql --force 越过 SIGNAL 继续执行破坏性语句（破坏性操作全在被门控的存储过程内，
--        过程外的语句仅 DROP PROCEDURE 等无害清理）
--     4. createdAt IS NULL 的历史 PENDING 行被静默漏掉（dry-run 单独列出，apply 需显式确认）
--
--   第一次执行（dry-run，采集快照 + 出清单，不改动业务数据）：
--     SET @patch15_cutoff := '<旧版应用最后一次写入或旧迁移执行的时间点，如 2026-08-06 00:00:00>';
--     SOURCE database/patches/15_team_content_history_publish.sql;
--     -- 脚本输出"将发布 N 条图片 / M 条文字"及 ID 清单（含 createdAt IS NULL 待确认行）后 SIGNAL 中止
--     -- 运维核对数量无误后：
--     SET @patch15_apply := 1;
--     SOURCE database/patches/15_team_content_history_publish.sql;  -- 第二次才执行文件搬迁 + 回填
--     bash database/patches/15_migrate_images_files.sh ...           -- 物理搬迁
--
--   cutoff 语义：只把 createdAt <= @patch15_cutoff 且 createdAt IS NOT NULL 的 PENDING 行
--   视作"历史待回填"；上线后新行 createdAt > cutoff 不进快照、不会被回填公开。
--   createdAt IS NULL 的 PENDING 行不会被自动回填，dry-run 会列出并要求
--   SET @patch15_confirm_null_created := 1 显式确认（或先补齐 createdAt）才能 apply。
--
--   状态机：_patch15_meta 持久化 dry-run 的 cutoff 与 applied_at；快照表 _patch15_hist_*
--   只冻结候选 ID。apply 前强校验 meta.cutoff == 本次 cutoff 且快照与候选集完全一致
--   （行集对比，stale/miss 均为 0），任一不一致即 SIGNAL 并要求重新 dry-run，
--   杜绝"错误 cutoff 快照被复用"。
--
--   全部破坏性语句（清单表 / 改指 imageUrl / 回填 PUBLISHED / 清理旧快照）都在存储过程
--   patch15_main() 内、五道确认门全部通过后才执行；任何 SIGNAL 会中止整个过程。
--   即使客户端在 SIGNAL 后继续读取脚本，过程外只剩 DROP PROCEDURE 等无害语句。
--
--   仍须在停机窗口执行（停止应用写入团队表，见 patch 11 头注释的维护模式说明）。
-- =====================================================================

SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS patch15_main$$

CREATE PROCEDURE patch15_main()
BEGIN
    DECLARE v_is_apply INT;
    DECLARE v_meta_cutoff DATETIME;
    DECLARE v_meta_applied DATETIME;
    DECLARE v_snap_img_exists INT;
    DECLARE v_snap_word_exists INT;
    DECLARE v_stale_img INT;
    DECLARE v_miss_img INT;
    DECLARE v_stale_word INT;
    DECLARE v_miss_word INT;
    DECLARE v_snap_valid INT;
    DECLARE v_img_count INT;
    DECLARE v_word_count INT;
    DECLARE v_null_img INT;
    DECLARE v_null_word INT;

    -- -----------------------------------------------------------------
    -- 1. 完整前置 schema 校验（exy v6 P2#6，原第 1 节；搬入过程统一门控，
    --    SIGNAL 会中止整个过程，--force 客户端也无法继续执行后续破坏性语句）
    --    一次性校验全部依赖表/列/索引/唯一约束，缺任一项明确 SIGNAL 中止。
    -- -----------------------------------------------------------------
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_media') = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'patch 11 从未执行（team_media 表不存在），请先按顺序执行 10→11→12 后再执行 15';
    END IF;
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_images' AND COLUMN_NAME = 'team_id') = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'patch 11 部分执行（team_page_images.team_id 列不存在），请补跑 patch 11 后再执行 15';
    END IF;
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_images' AND COLUMN_NAME = 'status') = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'patch 11 部分执行（team_page_images.status 列不存在），请补跑 patch 11 后再执行 15';
    END IF;
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_images' AND COLUMN_NAME = 'createdAt') = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'team_page_images.createdAt 列不存在（cutoff 回填依赖该列），请核对 ganlu.sql 基线';
    END IF;
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_word' AND COLUMN_NAME = 'team_id') = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'patch 11 部分执行（team_page_word.team_id 列不存在），请补跑 patch 11 后再执行 15';
    END IF;
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_word' AND COLUMN_NAME = 'status') = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'patch 11 部分执行（team_page_word.status 列不存在），请补跑 patch 11 后再执行 15';
    END IF;
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_word' AND COLUMN_NAME = 'createdAt') = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'team_page_word.createdAt 列不存在（cutoff 回填依赖该列），请核对 ganlu.sql 基线';
    END IF;
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_images'
            AND INDEX_NAME IN ('idx_team_id', 'idx_status')) < 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'patch 11 部分执行（team_page_images 缺 idx_team_id/idx_status 索引），请补跑 patch 11';
    END IF;
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team_page_word'
            AND INDEX_NAME IN ('idx_team_id', 'idx_status')) < 2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'patch 11 部分执行（team_page_word 缺 idx_team_id/idx_status 索引），请补跑 patch 11';
    END IF;
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'team' AND INDEX_NAME = 'uk_team_owner_user') = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'patch 12 未执行（team.uk_team_owner_user 唯一约束不存在），请先执行 12 后再执行 15';
    END IF;

    -- -----------------------------------------------------------------
    -- 2. cutoff 强制校验（原第 2 节）
    -- -----------------------------------------------------------------
    IF @patch15_cutoff IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '未提供 @patch15_cutoff。请先 SET @patch15_cutoff := ''<旧版应用最后一次写入时间点>'' 后重跑（dry-run 会输出待发布清单并中止）';
    END IF;

    -- -----------------------------------------------------------------
    -- 3. 状态机采集：确保 _patch15_meta 与快照表存在且与本次 cutoff 一致
    -- -----------------------------------------------------------------
    CREATE TABLE IF NOT EXISTS `_patch15_meta` (
      `id` tinyint NOT NULL DEFAULT 1,
      `cutoff` datetime NULL COMMENT 'dry-run 使用的 cutoff',
      `snapshot_images` int NOT NULL DEFAULT 0 COMMENT 'dry-run 采集的图片快照行数（审计）',
      `snapshot_word` int NOT NULL DEFAULT 0 COMMENT 'dry-run 采集的文字快照行数（审计）',
      `null_created_images` int NOT NULL DEFAULT 0 COMMENT 'dry-run 时 createdAt IS NULL 的 PENDING 图片行数',
      `null_created_word` int NOT NULL DEFAULT 0 COMMENT 'dry-run 时 createdAt IS NULL 的 PENDING 文字行数',
      `applied_at` datetime NULL COMMENT '回填实际执行时间（非 NULL 表示已应用）',
      PRIMARY KEY (`id`)
    ) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci;

    -- 读 meta（本地变量默认 NULL，不依赖会话变量残留）
    SELECT `cutoff`, `applied_at` INTO v_meta_cutoff, v_meta_applied FROM `_patch15_meta` WHERE id = 1;

    -- 快照精确性：行集对比（stale=快照里不属于当前候选集的行；miss=候选集里不在快照的行）
    SET v_snap_img_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '_patch15_hist_images');
    SET v_snap_word_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '_patch15_hist_word');
    SET v_snap_valid := 0;
    IF v_snap_img_exists > 0 AND v_snap_word_exists > 0 THEN
        SELECT COUNT(*) INTO v_stale_img FROM `_patch15_hist_images` h
          LEFT JOIN `team_page_images` img ON img.id = h.id
         WHERE img.id IS NULL OR img.team_id IS NULL OR img.status <> 'PENDING'
            OR img.createdAt IS NULL OR img.createdAt > @patch15_cutoff;
        SELECT COUNT(*) INTO v_miss_img FROM `team_page_images` img
          LEFT JOIN `_patch15_hist_images` h ON img.id = h.id
         WHERE img.team_id IS NOT NULL AND img.status = 'PENDING'
           AND img.createdAt IS NOT NULL AND img.createdAt <= @patch15_cutoff
           AND h.id IS NULL;
        SELECT COUNT(*) INTO v_stale_word FROM `_patch15_hist_word` h
          LEFT JOIN `team_page_word` wrd ON wrd.id = h.id
         WHERE wrd.id IS NULL OR wrd.team_id IS NULL OR wrd.status <> 'PENDING'
            OR wrd.createdAt IS NULL OR wrd.createdAt > @patch15_cutoff;
        SELECT COUNT(*) INTO v_miss_word FROM `team_page_word` wrd
          LEFT JOIN `_patch15_hist_word` h ON wrd.id = h.id
         WHERE wrd.team_id IS NOT NULL AND wrd.status = 'PENDING'
           AND wrd.createdAt IS NOT NULL AND wrd.createdAt <= @patch15_cutoff
           AND h.id IS NULL;
        IF v_stale_img = 0 AND v_miss_img = 0 AND v_stale_word = 0 AND v_miss_word = 0
           AND v_meta_cutoff IS NOT NULL AND v_meta_cutoff = @patch15_cutoff THEN
            SET v_snap_valid := 1;
        END IF;
    END IF;

    -- 模式：apply = 严格 COALESCE(apply,0)=1
    SET v_is_apply := (COALESCE(@patch15_apply, 0) = 1);

    -- dry-run 模式（v_is_apply=0）且快照无效 → 重建快照并更新 meta（审计保留 applied_at，不重置）
    -- apply 模式（v_is_apply=1）绝不重建——快照必须来自同 cutoff 的 dry-run，由第 5 节门校验
    IF v_is_apply = 0 AND v_snap_valid = 0 THEN
        DROP TABLE IF EXISTS `_patch15_hist_images`;
        CREATE TABLE `_patch15_hist_images` (PRIMARY KEY (`id`)) ENGINE = InnoDB AS
          SELECT `id` FROM `team_page_images`
           WHERE team_id IS NOT NULL AND status = 'PENDING'
             AND createdAt IS NOT NULL AND createdAt <= @patch15_cutoff;
        DROP TABLE IF EXISTS `_patch15_hist_word`;
        CREATE TABLE `_patch15_hist_word` (PRIMARY KEY (`id`)) ENGINE = InnoDB AS
          SELECT `id` FROM `team_page_word`
           WHERE team_id IS NOT NULL AND status = 'PENDING'
             AND createdAt IS NOT NULL AND createdAt <= @patch15_cutoff;
        SELECT COUNT(*) INTO v_img_count FROM `_patch15_hist_images`;
        SELECT COUNT(*) INTO v_word_count FROM `_patch15_hist_word`;
        SELECT COUNT(*) INTO v_null_img FROM `team_page_images`
          WHERE team_id IS NOT NULL AND status = 'PENDING' AND createdAt IS NULL;
        SELECT COUNT(*) INTO v_null_word FROM `team_page_word`
          WHERE team_id IS NOT NULL AND status = 'PENDING' AND createdAt IS NULL;
        INSERT INTO `_patch15_meta`
            (id, cutoff, snapshot_images, snapshot_word, null_created_images, null_created_word, applied_at)
        VALUES (1, @patch15_cutoff, v_img_count, v_word_count, v_null_img, v_null_word, NULL)
        ON DUPLICATE KEY UPDATE
            cutoff = @patch15_cutoff,
            snapshot_images = v_img_count,
            snapshot_word = v_word_count,
            null_created_images = v_null_img,
            null_created_word = v_null_word,
            applied_at = applied_at; -- 保留已应用标记，dry-run 不重置
        SET v_snap_valid := 1;
        SET v_meta_cutoff := @patch15_cutoff;
    ELSE
        -- 快照已有效或为 apply 模式：仅重算展示用计数（不重建）
        SELECT COUNT(*) INTO v_img_count FROM `_patch15_hist_images`;
        SELECT COUNT(*) INTO v_word_count FROM `_patch15_hist_word`;
        SELECT COUNT(*) INTO v_null_img FROM `team_page_images`
          WHERE team_id IS NOT NULL AND status = 'PENDING' AND createdAt IS NULL;
        SELECT COUNT(*) INTO v_null_word FROM `team_page_word`
          WHERE team_id IS NOT NULL AND status = 'PENDING' AND createdAt IS NULL;
    END IF;

    -- -----------------------------------------------------------------
    -- 4. preview（始终输出，供运维核对；含 createdAt IS NULL 待确认行，不再静默）
    -- -----------------------------------------------------------------
    SELECT CONCAT('待发布图片 ', v_img_count, ' 条 / 待发布文字 ', v_word_count, ' 条',
                  ' / createdAt IS NULL 待确认图片 ', v_null_img, ' 条 / 文字 ', v_null_word, ' 条',
                  '（cutoff=', @patch15_cutoff, '）') AS preview;
    SELECT '待发布图片 ID 清单（createdAt <= cutoff）' AS section;
    SELECT h.id FROM `_patch15_hist_images` h ORDER BY h.id;
    SELECT '待发布文字 ID 清单（createdAt <= cutoff）' AS section;
    SELECT h.id FROM `_patch15_hist_word` h ORDER BY h.id;
    IF v_null_img > 0 THEN
        SELECT 'createdAt IS NULL 的 PENDING 图片 ID（不会被自动回填，需人工确认）' AS section;
        SELECT id FROM `team_page_images`
         WHERE team_id IS NOT NULL AND status = 'PENDING' AND createdAt IS NULL ORDER BY id;
    END IF;
    IF v_null_word > 0 THEN
        SELECT 'createdAt IS NULL 的 PENDING 文字 ID（不会被自动回填，需人工确认）' AS section;
        SELECT id FROM `team_page_word`
         WHERE team_id IS NOT NULL AND status = 'PENDING' AND createdAt IS NULL ORDER BY id;
    END IF;

    -- -----------------------------------------------------------------
    -- 5. 确认门（任一不过即 SIGNAL 中止整个过程；破坏性语句在门后才会执行）
    -- -----------------------------------------------------------------
    -- 5.1 dry-run 门：严格 =1，任何非 1 值（含 0）都视为未确认
    IF COALESCE(@patch15_apply, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dry-run 完成（已输出清单）。核对数量无误后，SET @patch15_apply := 1 重跑本文件执行回填；不回填请勿设该变量';
    END IF;
    -- 5.2 cutoff 一致性门：快照必须是本次 cutoff 的 dry-run 产物
    IF v_meta_cutoff IS NULL OR v_meta_cutoff <> @patch15_cutoff THEN
        SET @msg := CONCAT('cutoff 与 dry-run 不一致（快照 cutoff = ', IFNULL(CAST(v_meta_cutoff AS CHAR), '未采集'), '，本次 = ', CAST(@patch15_cutoff AS CHAR), '）。请用相同 cutoff 重新 dry-run 后 apply');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @msg;
    END IF;
    -- 5.3 已应用门：防重复执行（放快照一致性前，重复 apply 时给出明确提示）
    IF v_meta_applied IS NOT NULL THEN
        SET @msg := CONCAT('本 patch 已应用过（applied_at = ', IFNULL(CAST(v_meta_applied AS CHAR), ''), '）。如需重跑请先清空 _patch15_meta 与 _patch15_hist_*');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @msg;
    END IF;
    -- 5.4 快照一致性门：apply 前快照必须仍与候选集完全一致
    IF v_snap_valid = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '快照与当前数据不一致（有新行或状态变化）。请重新 dry-run 采集后 apply';
    END IF;
    -- 5.5 createdAt IS NULL 确认门：不静默吞掉，需显式确认
    IF (v_null_img + v_null_word) > 0 AND COALESCE(@patch15_confirm_null_created, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '存在 createdAt IS NULL 的 PENDING 历史行（见清单）。这些行不会被自动回填；如已知悉请 SET @patch15_confirm_null_created := 1 后重跑';
    END IF;

    -- -----------------------------------------------------------------
    -- 6. 文件搬迁前置数据迁移（原第 5 节）— 持久化迁移清单表
    --    仅在确认运行（门全部通过）阶段执行。统一归一化 images/ 与 Windows images\ 两种前缀，
    --    配套搬迁脚本只消费该清单，精确搬迁。幂等：LEFT JOIN ... WHERE m.id IS NULL 跳过已采集行。
    -- -----------------------------------------------------------------
    CREATE TABLE IF NOT EXISTS `_patch15_image_migration` (
      `id` int(0) NOT NULL,
      `old_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
      `new_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
      PRIMARY KEY (`id`) USING BTREE
    ) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci;

    INSERT INTO `_patch15_image_migration` (id, old_url, new_url)
      SELECT img.id, img.imageUrl, CONCAT('images_pending/', SUBSTRING(img.imageUrl, 8))
        FROM `team_page_images` img
        LEFT JOIN `_patch15_image_migration` m ON m.id = img.id
       WHERE m.id IS NULL AND LEFT(img.imageUrl, 7) = 'images/';

    INSERT INTO `_patch15_image_migration` (id, old_url, new_url)
      SELECT img.id, img.imageUrl, CONCAT('images_pending/', SUBSTRING(img.imageUrl, 8))
        FROM `team_page_images` img
        LEFT JOIN `_patch15_image_migration` m ON m.id = img.id
       WHERE m.id IS NULL
         AND LEFT(img.imageUrl, 6) = 'images'
         AND SUBSTRING(img.imageUrl, 7, 1) = CHAR(92);

    UPDATE `team_page_images` img
      JOIN `_patch15_image_migration` m ON m.id = img.id
       SET img.imageUrl = m.new_url
     WHERE img.imageUrl = m.old_url;

    -- -----------------------------------------------------------------
    -- 7. 历史内容回填 PUBLISHED（原第 6 节）— 基于第 3 节快照
    --    只回填快照内、仍 PENDING 的行。天然幂等（已 PUBLISHED 的 WHERE status='PENDING' 不命中）。
    -- -----------------------------------------------------------------
    UPDATE `team_page_images` img
      JOIN `_patch15_hist_images` h ON h.id = img.id
       SET img.status = 'PUBLISHED'
     WHERE img.status = 'PENDING' AND img.team_id IS NOT NULL;

    UPDATE `team_page_word` wrd
      JOIN `_patch15_hist_word` h ON h.id = wrd.id
       SET wrd.status = 'PUBLISHED'
     WHERE wrd.status = 'PENDING' AND wrd.team_id IS NOT NULL;

    -- -----------------------------------------------------------------
    -- 8. 清理旧版残留（原第 7 节）— 不 DROP（防误删快照数据），RENAME 备份保留审计依据
    -- -----------------------------------------------------------------
    SET @old_snap_images := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '_patch11_hist_images');
    IF @old_snap_images > 0 THEN
        SET @sql := 'RENAME TABLE `_patch11_hist_images` TO `_patch11_hist_images_deprecated`';
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
    SET @old_snap_word := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '_patch11_hist_word');
    IF @old_snap_word > 0 THEN
        SET @sql := 'RENAME TABLE `_patch11_hist_word` TO `_patch11_hist_word_deprecated`';
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    -- -----------------------------------------------------------------
    -- 9. 标记已应用 + 成功消息（仅门全过后到达此处）
    -- -----------------------------------------------------------------
    UPDATE `_patch15_meta` SET applied_at = NOW() WHERE id = 1;
    SELECT 'patch 15 历史回填 + 文件搬迁清单完成（请执行 15_migrate_images_files.sh 完成物理搬迁）' AS migration_result;
END$$

CALL patch15_main()$$

DROP PROCEDURE patch15_main$$

DELIMITER ;
