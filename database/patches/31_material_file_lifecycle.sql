-- Patch 31: 课件文件共享路径预检与持久化生命周期任务
-- 依赖：14_team_media_lifecycle.sql、30_material_center.sql。
-- 本脚本只做阻断检查，不擅自复制或改写生产文件；发现共享路径后必须先在备份副本拆分文件。
SET NAMES utf8mb4;

-- 先列出全部阻断项，便于保存审计记录并逐条拆分。
SELECT normalized_path,
       COUNT(DISTINCT course_id) AS active_course_count,
       GROUP_CONCAT(DISTINCT CONCAT(file_role, '#', course_id) ORDER BY course_id, file_role) AS references_to_split
FROM (
    SELECT id AS course_id, 'COVER' AS file_role,
           TRIM(LEADING '/' FROM REPLACE(
               COALESCE(NULLIF(TRIM(cover_path), ''), NULLIF(TRIM(thumbnail_url), '')), CHAR(92), '/')) AS normalized_path
    FROM course_detail WHERE status = 1
    UNION ALL
    SELECT id, 'ORIGINAL',
           TRIM(LEADING '/' FROM REPLACE(
               COALESCE(NULLIF(TRIM(original_file_path), ''), NULLIF(TRIM(files), '')), CHAR(92), '/'))
    FROM course_detail WHERE status = 1
    UNION ALL
    SELECT id, 'PREVIEW',
           TRIM(LEADING '/' FROM REPLACE(NULLIF(TRIM(preview_file_path), ''), CHAR(92), '/'))
    FROM course_detail WHERE status = 1
) material_paths
WHERE normalized_path IS NOT NULL AND normalized_path <> ''
GROUP BY normalized_path
HAVING COUNT(DISTINCT course_id) > 1
ORDER BY normalized_path;

DROP PROCEDURE IF EXISTS check_shared_material_paths;
DELIMITER $$
CREATE PROCEDURE check_shared_material_paths()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM (
            SELECT normalized_path
            FROM (
                SELECT id AS course_id,
                       TRIM(LEADING '/' FROM REPLACE(
                           COALESCE(NULLIF(TRIM(cover_path), ''), NULLIF(TRIM(thumbnail_url), '')), CHAR(92), '/')) AS normalized_path
                FROM course_detail WHERE status = 1
                UNION ALL
                SELECT id,
                       TRIM(LEADING '/' FROM REPLACE(
                           COALESCE(NULLIF(TRIM(original_file_path), ''), NULLIF(TRIM(files), '')), CHAR(92), '/'))
                FROM course_detail WHERE status = 1
                UNION ALL
                SELECT id,
                       TRIM(LEADING '/' FROM REPLACE(NULLIF(TRIM(preview_file_path), ''), CHAR(92), '/'))
                FROM course_detail WHERE status = 1
            ) all_material_paths
            WHERE normalized_path IS NOT NULL AND normalized_path <> ''
            GROUP BY normalized_path
            HAVING COUNT(DISTINCT course_id) > 1
        ) shared_material_paths
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '31_material_file_lifecycle.sql: 多条有效课件共享物理文件；请按上方列表复制拆分并更新路径后重试';
    END IF;
END$$
DELIMITER ;
CALL check_shared_material_paths();
DROP PROCEDURE IF EXISTS check_shared_material_paths;

-- 扩展既有 outbox 类型；课件软删除与三个角色任务由应用在同一事务写入。
SET @ddl = IF((
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'file_deletion_task'
      AND CONSTRAINT_NAME = 'chk_file_deletion_type'
) > 0,
    'ALTER TABLE file_deletion_task DROP CHECK chk_file_deletion_type',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE file_deletion_task
    ADD CONSTRAINT chk_file_deletion_type CHECK (asset_type IN (
        'PUBLIC_IMAGE','TEAM_MEDIA','COURSE_COVER','COURSE_ORIGINAL','COURSE_PREVIEW','COURSE_ORPHAN'
    ));

-- 验收：应返回 0；非 0 表示部署前仍有需要拆分的有效课件共享文件。
SELECT COUNT(*) AS remaining_shared_material_paths
FROM (
    SELECT normalized_path
    FROM (
        SELECT id AS course_id,
               TRIM(LEADING '/' FROM REPLACE(
                   COALESCE(NULLIF(TRIM(cover_path), ''), NULLIF(TRIM(thumbnail_url), '')), CHAR(92), '/')) AS normalized_path
        FROM course_detail WHERE status = 1
        UNION ALL
        SELECT id,
               TRIM(LEADING '/' FROM REPLACE(
                   COALESCE(NULLIF(TRIM(original_file_path), ''), NULLIF(TRIM(files), '')), CHAR(92), '/'))
        FROM course_detail WHERE status = 1
        UNION ALL
        SELECT id,
               TRIM(LEADING '/' FROM REPLACE(NULLIF(TRIM(preview_file_path), ''), CHAR(92), '/'))
        FROM course_detail WHERE status = 1
    ) all_material_paths
    WHERE normalized_path IS NOT NULL AND normalized_path <> ''
    GROUP BY normalized_path
    HAVING COUNT(DISTINCT course_id) > 1
) remaining;
