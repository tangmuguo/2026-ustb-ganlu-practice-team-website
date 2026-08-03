-- 公共图片永久配额账本
-- 执行顺序：11_team_content.sql 之后、20_message_board.sql 之前。
-- 仅创建账本并登记可识别的旧团队风采图片，不删除业务数据。

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
    relative_path VARCHAR(512) NOT NULL COMMENT '相对上传根目录的文件路径',
    owner_user_id INT NOT NULL COMMENT '上传账号ID',
    file_size BIGINT NOT NULL COMMENT '文件字节数；迁移前旧文件无法获知时为0',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (relative_path),
    KEY idx_public_image_asset_owner (owner_user_id),
    CONSTRAINT chk_public_image_asset_size CHECK (file_size >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已转正公共图片所有者与大小';

-- 旧入口保存的团队风采图片可确定所属账号，但 SQL 无法安全读取磁盘文件大小。
-- 迁移时至少把它们计入永久文件数量；后续新图片会记录真实字节数。
INSERT IGNORE INTO public_image_asset(relative_path, owner_user_id, file_size)
SELECT imageUrl, userId, 0
FROM team_page_images
WHERE userId IS NOT NULL
  AND imageUrl REGEXP '^images/[0-9a-fA-F-]{36}\\.(jpg|png|webp)$';

INSERT INTO public_image_quota(owner_user_id, used_file_count, used_bytes)
SELECT owner_user_id, COUNT(*), COALESCE(SUM(file_size), 0)
FROM public_image_asset
GROUP BY owner_user_id
ON DUPLICATE KEY UPDATE
    used_file_count = GREATEST(used_file_count, VALUES(used_file_count)),
    used_bytes = GREATEST(used_bytes, VALUES(used_bytes));
