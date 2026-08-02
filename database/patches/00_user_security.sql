-- 用户账号安全约束补丁。
-- 执行前必须备份数据库；若存在空账号或重复账号/手机号，脚本会停止，先人工清理数据。

DROP PROCEDURE IF EXISTS `apply_user_security_patch`;
DELIMITER $$
CREATE PROCEDURE `apply_user_security_patch`()
BEGIN
  IF EXISTS (SELECT 1 FROM `user` WHERE `username` IS NULL OR TRIM(`username`) = '' LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '00_user_security.sql: 存在空用户名，请先人工处理';
  END IF;

  IF EXISTS (SELECT `username` FROM `user` GROUP BY `username` HAVING COUNT(*) > 1 LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '00_user_security.sql: 存在重复用户名，请先人工处理';
  END IF;

  IF EXISTS (SELECT `phone` FROM `user` WHERE `phone` IS NOT NULL AND TRIM(`phone`) <> '' GROUP BY `phone` HAVING COUNT(*) > 1 LIMIT 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '00_user_security.sql: 存在重复手机号，请先人工处理';
  END IF;

  UPDATE `user` SET `phone` = NULL WHERE `phone` IS NOT NULL AND TRIM(`phone`) = '';
  ALTER TABLE `user`
    MODIFY COLUMN `username` VARCHAR(100) NOT NULL,
    MODIFY COLUMN `password` VARCHAR(255) NOT NULL,
    MODIFY COLUMN `phone` VARCHAR(20) NULL;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'user' AND index_name = 'uk_user_username'
  ) THEN
    ALTER TABLE `user` ADD UNIQUE KEY `uk_user_username` (`username`);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'user' AND index_name = 'uk_user_phone'
  ) THEN
    ALTER TABLE `user` ADD UNIQUE KEY `uk_user_phone` (`phone`);
  END IF;
END$$
DELIMITER ;

CALL `apply_user_security_patch`();
DROP PROCEDURE `apply_user_security_patch`;
