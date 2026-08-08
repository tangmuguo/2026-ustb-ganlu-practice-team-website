-- =====================================================================
-- Patch 12: team.owner_user_id 唯一约束
-- 依赖: ganlu.sql → 10_team_core.sql → 11_team_content.sql → 本文件
-- 语义: 一个甘露团队账号（level=1 user）最多负责一个小队（1:1 合同）。
--       这与现有 resolveTeamId 从 Token 单推 teamId、前端无选队 UI 的设计一致。
-- 可重入: 通过 INFORMATION_SCHEMA 判断 uk_team_owner_user 是否已存在。
-- 前置校验: 若已存在同一 owner_user_id 绑定多个小队，SIGNAL 中止，要求先人工澄清。
-- =====================================================================

SET NAMES utf8mb4;

DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_team_owner_unique_20260802$$
CREATE PROCEDURE migrate_team_owner_unique_20260802()
BEGIN
    DECLARE uk_exists INT DEFAULT 0;
    DECLARE duplicate_owner INT DEFAULT 0;

    -- 1. 检查唯一约束是否已存在（可重入）
    SELECT COUNT(1) INTO uk_exists
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'team'
       AND index_name = 'uk_team_owner_user';

    IF uk_exists = 0 THEN
        -- 2. 前置校验：若存在同一负责人绑定多个非归档小队，中止
        --    （归档小队仍占用 owner 不算冲突场景；此处检查所有状态，
        --     因 Patch 10 后 owner_user_id NOT NULL，历史归档小队也应独占 owner）
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
                SET MESSAGE_TEXT = '存在同一负责人账号绑定多个小队，请先合并/解绑后再执行 12_team_owner_unique.sql';
        END IF;

        -- 3. 加唯一约束
        ALTER TABLE team ADD UNIQUE KEY uk_team_owner_user (owner_user_id);
    END IF;
END$$
CALL migrate_team_owner_unique_20260802()$$
DROP PROCEDURE IF EXISTS migrate_team_owner_unique_20260802$$
DELIMITER ;

SELECT 'team.owner_user_id 唯一约束迁移完成' AS migration_result;
