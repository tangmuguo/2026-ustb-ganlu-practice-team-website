-- 团队风采核心数据迁移
-- 执行顺序：先导入根目录 ganlu.sql，再执行本脚本。
-- 本脚本不删除业务表；迁移前会备份 team 和 team_page。
-- 如旧数据无法唯一映射，脚本会在改表前 SIGNAL 中止并给出明确原因。

SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS migrate_team_core_20260730$$
CREATE PROCEDURE migrate_team_core_20260730()
BEGIN
    DECLARE changed_column_count INT DEFAULT 0;
    DECLARE backup_table_count INT DEFAULT 0;

    SELECT COUNT(1)
      INTO changed_column_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND ((table_name = 'team' AND column_name = 'owner_user_id')
         OR (table_name = 'team_page' AND column_name = 'team_id'));

    IF changed_column_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '10_team_core.sql 已执行或曾部分执行；请先核对现有字段和备份，不要重复运行';
    END IF;

    SELECT COUNT(1)
      INTO backup_table_count
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name IN (
           'team_core_backup_team_20260730',
           'team_core_backup_team_page_20260730'
       );

    IF backup_table_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '检测到 team_core_backup_*_20260730 备份表；请先确认上次迁移状态';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM team
         WHERE year IS NULL
            OR year NOT REGEXP '^[0-9]{4}$'
            OR CAST(year AS UNSIGNED) < 1900
            OR CAST(year AS UNSIGNED) > 2100
            OR name IS NULL
            OR TRIM(name) = ''
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'team 存在非法年份或空名称；请修复后重新执行';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM team
         GROUP BY year, name
        HAVING COUNT(1) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'team 存在重复的 year + name；请先合并重复小队';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM team t
          LEFT JOIN `user` u
            ON u.level = 1
           AND u.teamname IS NOT NULL
           AND TRIM(u.teamname) = TRIM(t.name)
         GROUP BY t.id
        HAVING COUNT(u.id) != 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '旧 team 无法按团队名唯一绑定 level=1 账号；请先修正 user.teamname';
    END IF;

    DROP TEMPORARY TABLE IF EXISTS tmp_team_page_mapping;
    CREATE TEMPORARY TABLE tmp_team_page_mapping (
        page_id INT NOT NULL,
        team_id INT NOT NULL,
        PRIMARY KEY (page_id, team_id)
    ) ENGINE = InnoDB;

    -- 兼容旧 userId 实际存放 team.id 的数据。
    INSERT IGNORE INTO tmp_team_page_mapping (page_id, team_id)
    SELECT tp.id, t.id
      FROM team_page tp
      JOIN team t ON t.id = tp.userId;

    -- 兼容旧 userId 实际存放团队账号 user.id 的数据。
    INSERT IGNORE INTO tmp_team_page_mapping (page_id, team_id)
    SELECT tp.id, t.id
      FROM team_page tp
      JOIN `user` u
        ON u.id = tp.userId
       AND u.level = 1
      JOIN team t
        ON u.teamname IS NOT NULL
       AND TRIM(t.name) = TRIM(u.teamname);

    IF EXISTS (
        SELECT 1
          FROM team_page tp
          LEFT JOIN (
              SELECT page_id, COUNT(1) AS candidate_count
                FROM tmp_team_page_mapping
               GROUP BY page_id
          ) mapping_count ON mapping_count.page_id = tp.id
         WHERE COALESCE(mapping_count.candidate_count, 0) != 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'team_page.userId 不能唯一映射为 team.id；请根据备份前数据手工澄清';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM tmp_team_page_mapping
         GROUP BY team_id
        HAVING COUNT(1) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '同一 team 存在多个 team_page；请先合并详情页';
    END IF;

    CREATE TABLE team_core_backup_team_20260730 LIKE team;
    INSERT INTO team_core_backup_team_20260730 SELECT * FROM team;

    CREATE TABLE team_core_backup_team_page_20260730 LIKE team_page;
    INSERT INTO team_core_backup_team_page_20260730 SELECT * FROM team_page;

    ALTER TABLE team
        ADD COLUMN owner_user_id INT NULL COMMENT '绑定的团队账号ID' AFTER name,
        ADD COLUMN region VARCHAR(100) NULL COMMENT '支教地区' AFTER owner_user_id,
        ADD COLUMN school VARCHAR(150) NULL COMMENT '支教学校' AFTER region,
        ADD COLUMN description TEXT NULL COMMENT '小队简介' AFTER school,
        ADD COLUMN cover_url VARCHAR(512) NULL COMMENT '封面图地址' AFTER description,
        ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ARCHIVED' AFTER cover_url,
        ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间' AFTER status,
        ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER created_at;

    UPDATE team t
    JOIN `user` u
      ON u.level = 1
     AND u.teamname IS NOT NULL
     AND TRIM(u.teamname) = TRIM(t.name)
       SET t.owner_user_id = u.id,
           t.region = NULLIF(TRIM(u.helplocation), ''),
           t.school = NULLIF(TRIM(u.helpschool), ''),
           t.cover_url = NULLIF(TRIM(u.imageUrl), '');

    ALTER TABLE team_page
        ADD COLUMN team_id INT NULL COMMENT '所属小队ID' AFTER updated_at;

    UPDATE team_page tp
    JOIN tmp_team_page_mapping mapping ON mapping.page_id = tp.id
       SET tp.team_id = mapping.team_id;

    UPDATE team_page
       SET status = '草稿'
     WHERE status IS NULL;

    UPDATE team t
    LEFT JOIN team_page tp ON tp.team_id = t.id
       SET t.status = CASE tp.status
           WHEN '展示' THEN 'PUBLISHED'
           WHEN '归档' THEN 'ARCHIVED'
           ELSE 'DRAFT'
       END;

    ALTER TABLE team
        MODIFY COLUMN year CHAR(4) NOT NULL COMMENT '4位年份',
        MODIFY COLUMN name VARCHAR(100) NOT NULL COMMENT '团队名',
        MODIFY COLUMN owner_user_id INT NOT NULL COMMENT '绑定的 level=1 团队账号ID',
        ADD CONSTRAINT chk_team_year
            CHECK (year REGEXP '^[0-9]{4}$' AND CAST(year AS UNSIGNED) BETWEEN 1900 AND 2100),
        ADD CONSTRAINT chk_team_status
            CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
        ADD UNIQUE KEY uk_team_year_name (year, name),
        ADD KEY idx_team_year_status (year, status),
        ADD CONSTRAINT fk_team_owner_user
            FOREIGN KEY (owner_user_id) REFERENCES `user` (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

    ALTER TABLE team_page
        MODIFY COLUMN team_id INT NOT NULL COMMENT '所属小队ID',
        MODIFY COLUMN status ENUM('草稿', '展示', '归档') NOT NULL DEFAULT '草稿' COMMENT '页面状态',
        DROP COLUMN userId,
        ADD UNIQUE KEY uk_team_page_team_id (team_id),
        ADD CONSTRAINT fk_team_page_team
            FOREIGN KEY (team_id) REFERENCES team (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;

    DROP TEMPORARY TABLE IF EXISTS tmp_team_page_mapping;

    SELECT '团队风采核心迁移完成；请保留 team_core_backup_team_20260730 和 team_core_backup_team_page_20260730 用于验收' AS migration_result;
END$$

CALL migrate_team_core_20260730()$$
DROP PROCEDURE IF EXISTS migrate_team_core_20260730$$

DELIMITER ;
