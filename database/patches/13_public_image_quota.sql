-- Patch 13: 公共图片永久配额账本与稳定资源编号
-- 执行顺序：12_team_owner_unique.sql 之后、20_message_board.sql 之前。
-- 仅创建账本结构，不猜测旧图片大小、所有者或共享关系。
-- 执行后必须先调用管理员预检/迁移程序，使用磁盘真实大小登记全部业务图片。
-- 课件封面由课件模块独立管理；应用预检会保护 images/materials 及有效课件引用的历史封面。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS public_image_quota (
    owner_user_id INT NOT NULL COMMENT '上传账号ID',
    used_file_count INT NOT NULL DEFAULT 0 COMMENT '已转正图片数',
    used_bytes BIGINT NOT NULL DEFAULT 0 COMMENT '已转正图片累计字节数',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (owner_user_id),
    CONSTRAINT chk_public_image_quota_count CHECK (used_file_count >= 0),
    CONSTRAINT chk_public_image_quota_bytes CHECK (used_bytes >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共图片永久配额原子账本';

CREATE TABLE IF NOT EXISTS public_image_asset (
    asset_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '稳定资源编号；文件移动时保持不变',
    relative_path VARCHAR(512) NOT NULL COMMENT '相对上传根目录的文件路径',
    owner_user_id INT NOT NULL COMMENT '上传账号ID',
    file_size BIGINT NOT NULL COMMENT '文件真实字节数；禁止用0代替未知大小',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (asset_id),
    UNIQUE KEY uk_public_image_asset_path (relative_path),
    KEY idx_public_image_asset_owner (owner_user_id),
    CONSTRAINT chk_public_image_asset_size CHECK (file_size >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已转正公共图片所有者与大小';

-- 兼容已试跑旧版 12_public_image_quota.sql 的数据库：把“路径主键”升级为稳定资源编号。
SET @asset_id_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'public_image_asset' AND COLUMN_NAME = 'asset_id');
SET @sql := IF(@asset_id_exists = 0,
  'ALTER TABLE public_image_asset DROP PRIMARY KEY, ADD COLUMN asset_id BIGINT NOT NULL AUTO_INCREMENT FIRST, ADD PRIMARY KEY (asset_id), ADD UNIQUE KEY uk_public_image_asset_path (relative_path)',
  'SELECT ''public_image_asset.asset_id 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @asset_path_unique_exists := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'public_image_asset' AND INDEX_NAME = 'uk_public_image_asset_path');
SET @sql := IF(@asset_path_unique_exists = 0,
  'ALTER TABLE public_image_asset ADD UNIQUE KEY uk_public_image_asset_path (relative_path)',
  'SELECT ''public_image_asset 路径唯一索引已存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT CONCAT(
    '账本结构已创建。请先调用 GET /admin/public-image-migration/preflight；',
    '清零全部阻断项后，在维护窗口开启迁移开关并调用 POST /admin/public-image-migration/migrate。'
) AS migration_required;
