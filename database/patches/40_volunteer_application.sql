-- 志愿者报名表。执行前请先备份数据库。
CREATE TABLE IF NOT EXISTS `volunteer_application` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(30) NOT NULL,
  `phone` VARCHAR(20) NOT NULL,
  `organization` VARCHAR(100) NOT NULL,
  `grade_or_major` VARCHAR(100) NULL,
  `preferred_region` VARCHAR(100) NULL,
  `skills` VARCHAR(300) NULL,
  `introduction` VARCHAR(1000) NOT NULL,
  `privacy_agreed` TINYINT(1) NOT NULL DEFAULT 0,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `active_phone` VARCHAR(20) GENERATED ALWAYS AS (
    CASE WHEN `status` IN ('PENDING', 'CONTACTED') THEN `phone` ELSE NULL END
  ) STORED,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_volunteer_application_status_created` (`status`, `created_at`),
  KEY `idx_volunteer_application_phone_status` (`phone`, `status`),
  UNIQUE KEY `uk_volunteer_active_phone` (`active_phone`),
  CONSTRAINT `chk_volunteer_application_status`
    CHECK (`status` IN ('PENDING', 'CONTACTED', 'ACCEPTED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 兼容已经执行过旧版 40 补丁的数据库；发现存量重复时停止，交由人工确认。
DROP PROCEDURE IF EXISTS `apply_volunteer_active_phone_constraint`;
DELIMITER $$
CREATE PROCEDURE `apply_volunteer_active_phone_constraint`()
BEGIN
  IF EXISTS (
    SELECT `phone`
    FROM `volunteer_application`
    WHERE `status` IN ('PENDING', 'CONTACTED')
    GROUP BY `phone`
    HAVING COUNT(*) > 1
    LIMIT 1
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = '40_volunteer_application.sql: 存在同手机号的重复处理中报名，请先人工处理';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'volunteer_application'
      AND column_name = 'active_phone'
  ) THEN
    ALTER TABLE `volunteer_application`
      ADD COLUMN `active_phone` VARCHAR(20) GENERATED ALWAYS AS (
        CASE WHEN `status` IN ('PENDING', 'CONTACTED') THEN `phone` ELSE NULL END
      ) STORED AFTER `status`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'volunteer_application'
      AND index_name = 'uk_volunteer_active_phone'
  ) THEN
    ALTER TABLE `volunteer_application`
      ADD UNIQUE KEY `uk_volunteer_active_phone` (`active_phone`);
  END IF;
END$$
DELIMITER ;

CALL `apply_volunteer_active_phone_constraint`();
DROP PROCEDURE `apply_volunteer_active_phone_constraint`;
