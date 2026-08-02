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
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_volunteer_application_status_created` (`status`, `created_at`),
  KEY `idx_volunteer_application_phone_status` (`phone`, `status`),
  CONSTRAINT `chk_volunteer_application_status`
    CHECK (`status` IN ('PENDING', 'CONTACTED', 'ACCEPTED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
