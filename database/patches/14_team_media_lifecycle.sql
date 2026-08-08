-- Patch 14: 团队视频/附件原子配额与持久化删除任务
-- 执行顺序：13_public_image_quota.sql 之后，20_message_board.sql 之前。
-- 逻辑归档不会释放额度；只有物理文件删除成功（文件不存在也视为幂等成功）后才释放。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS team_media_quota (
    owner_user_id INT NOT NULL COMMENT '上传账号ID',
    used_file_count INT NOT NULL DEFAULT 0 COMMENT '含归档状态的附件数量',
    used_bytes BIGINT NOT NULL DEFAULT 0 COMMENT '含归档状态的累计字节数',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (owner_user_id),
    CONSTRAINT chk_team_media_quota_count CHECK (used_file_count >= 0),
    CONSTRAINT chk_team_media_quota_bytes CHECK (used_bytes >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团队附件账号级原子配额账本';

CREATE TABLE IF NOT EXISTS team_media_global_quota (
    singleton_id TINYINT NOT NULL,
    used_file_count INT NOT NULL DEFAULT 0 COMMENT '服务器全部团队附件数量',
    used_bytes BIGINT NOT NULL DEFAULT 0 COMMENT '服务器全部团队附件累计字节数',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (singleton_id),
    CONSTRAINT chk_team_media_global_singleton CHECK (singleton_id = 1),
    CONSTRAINT chk_team_media_global_count CHECK (used_file_count >= 0),
    CONSTRAINT chk_team_media_global_bytes CHECK (used_bytes >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团队附件服务器级原子配额账本';

CREATE TABLE IF NOT EXISTS team_media_upload_reservation (
    reservation_id CHAR(36) NOT NULL,
    owner_user_id INT NOT NULL COMMENT '已在读取请求体前完成认证的账号',
    reserved_bytes BIGINT NOT NULL COMMENT '跨实例原子登记的在途请求字节数',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP NOT NULL COMMENT '进程中断时的保守回收时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP NULL,
    PRIMARY KEY (reservation_id),
    KEY idx_team_media_upload_active (status, expires_at),
    KEY idx_team_media_upload_rate (owner_user_id, created_at),
    CONSTRAINT chk_team_media_upload_bytes CHECK (reserved_bytes > 0),
    CONSTRAINT chk_team_media_upload_status CHECK (status IN ('ACTIVE','RELEASED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Multipart解析前跨实例在途容量与速率记录';

CREATE TABLE IF NOT EXISTS file_deletion_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    asset_type VARCHAR(32) NOT NULL COMMENT '公共图片、团队附件或课件文件生命周期类型',
    asset_id BIGINT NOT NULL COMMENT '稳定资产ID或团队附件ID',
    relative_path VARCHAR(512) NOT NULL COMMENT '任务创建时路径；执行时优先读取资产当前路径',
    owner_user_id INT NOT NULL,
    file_size BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000) NULL,
    next_retry_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_deletion_asset (asset_type, asset_id),
    KEY idx_file_deletion_retry (status, next_retry_at),
    CONSTRAINT chk_file_deletion_size CHECK (file_size >= 0),
    CONSTRAINT chk_file_deletion_retry_count CHECK (retry_count >= 0),
    CONSTRAINT chk_file_deletion_status CHECK (status IN ('PENDING','FAILED')),
    CONSTRAINT chk_file_deletion_type CHECK (asset_type IN (
        'PUBLIC_IMAGE','TEAM_MEDIA','COURSE_COVER','COURSE_ORIGINAL','COURSE_PREVIEW','COURSE_ORPHAN'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可审计、可重试的文件删除 outbox';

-- 尽量为旧附件补齐上传账号；无法关联团队负责人的遗留数据需在联调前人工处理。
UPDATE team_media media
JOIN team t ON t.id = media.team_id
SET media.uploader_id = t.owner_user_id
WHERE media.uploader_id IS NULL AND t.owner_user_id IS NOT NULL;

UPDATE team_media SET file_size = 0 WHERE file_size IS NULL;

DROP PROCEDURE IF EXISTS check_team_media_quota_migration;
DELIMITER $$
CREATE PROCEDURE check_team_media_quota_migration()
BEGIN
    IF EXISTS (
        SELECT 1 FROM team_media
        WHERE uploader_id IS NULL OR file_size < 0
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '14_team_media_lifecycle.sql: 存在无法确定上传账号或大小非法的旧附件，请先人工修复';
    END IF;
END$$
DELIMITER ;
CALL check_team_media_quota_migration();
DROP PROCEDURE IF EXISTS check_team_media_quota_migration;

INSERT INTO team_media_quota(owner_user_id, used_file_count, used_bytes)
SELECT uploader_id, COUNT(*), COALESCE(SUM(file_size), 0)
FROM team_media
WHERE uploader_id IS NOT NULL
GROUP BY uploader_id
ON DUPLICATE KEY UPDATE
    used_file_count = GREATEST(used_file_count, VALUES(used_file_count)),
    used_bytes = GREATEST(used_bytes, VALUES(used_bytes));

INSERT INTO team_media_global_quota(singleton_id, used_file_count, used_bytes)
SELECT 1, COUNT(*), COALESCE(SUM(file_size), 0)
FROM team_media
WHERE uploader_id IS NOT NULL
ON DUPLICATE KEY UPDATE
    used_file_count = GREATEST(used_file_count, VALUES(used_file_count)),
    used_bytes = GREATEST(used_bytes, VALUES(used_bytes));
