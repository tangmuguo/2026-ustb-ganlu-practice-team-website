-- File security, quarantine and child-publication consent ledger.
--
-- This patch is additive and repeatable.  It deliberately does not rewrite
-- historical file rows or mark any historical content CLEAN/CONSENTED.  A
-- missing scan/consent row therefore fails closed in the application.  Run
-- only on a verified MySQL backup copy; this script is not a deployment step.

-- Existing rows receive the conservative PENDING default; no historical item
-- is marked CLEAN. MySQL 8's ADD COLUMN IF NOT EXISTS keeps this DDL
-- repeatable without dropping or rewriting any existing object or row.
ALTER TABLE team_media
    ADD COLUMN IF NOT EXISTS scan_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/INFECTED/CLEAN；仅 CLEAN 可下载或公开',
    ADD COLUMN IF NOT EXISTS scan_diagnostic_status VARCHAR(16) NULL
        COMMENT 'TIMEOUT/FAILED/UNAVAILABLE 等诊断结果';

ALTER TABLE team_page_images
    ADD COLUMN IF NOT EXISTS scan_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/INFECTED/CLEAN；仅 CLEAN 可下载或公开',
    ADD COLUMN IF NOT EXISTS scan_diagnostic_status VARCHAR(16) NULL
        COMMENT 'TIMEOUT/FAILED/UNAVAILABLE 等诊断结果';

ALTER TABLE team_page_word
    ADD COLUMN IF NOT EXISTS scan_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/INFECTED/CLEAN；仅 CLEAN 可下载或公开',
    ADD COLUMN IF NOT EXISTS scan_diagnostic_status VARCHAR(16) NULL
        COMMENT 'TIMEOUT/FAILED/UNAVAILABLE 等诊断结果';

ALTER TABLE course_detail
    ADD COLUMN IF NOT EXISTS scan_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/INFECTED/CLEAN；仅 CLEAN 可下载或公开',
    ADD COLUMN IF NOT EXISTS scan_diagnostic_status VARCHAR(16) NULL
        COMMENT 'TIMEOUT/FAILED/UNAVAILABLE 等诊断结果';

CREATE TABLE IF NOT EXISTS file_security_scan (
    id BIGINT NOT NULL AUTO_INCREMENT,
    storage_scope VARCHAR(32) NOT NULL,
    relative_path VARCHAR(500) NOT NULL,
    owner_user_id INT NULL,
    scan_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/CLEAN/INFECTED；非 CLEAN 不得公开或下载',
    diagnostic_status VARCHAR(16) NULL
        COMMENT 'CLEAN/INFECTED/TIMEOUT/FAILED/UNAVAILABLE',
    scanner_name VARCHAR(64) NULL,
    scanner_version VARCHAR(64) NULL,
    sha256 CHAR(64) NULL,
    detail VARCHAR(500) NULL,
    started_at DATETIME NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_security_scan_path (relative_path),
    KEY idx_file_security_scan_status_time (scan_status, updated_at),
    KEY idx_file_security_scan_scope_owner (storage_scope, owner_user_id, updated_at),
    CONSTRAINT chk_file_security_scan_status
        CHECK (scan_status IN ('PENDING', 'CLEAN', 'INFECTED')),
    CONSTRAINT chk_file_security_scan_sha256
        CHECK (sha256 IS NULL OR sha256 REGEXP '^[0-9A-Fa-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='隔离文件安全扫描账本；仅保存路径、摘要和最小诊断信息，不保存文件内容';

CREATE TABLE IF NOT EXISTS media_privacy_consent (
    id BIGINT NOT NULL AUTO_INCREMENT,
    asset_type VARCHAR(32) NOT NULL
        COMMENT 'CHILD_PHOTO/CHILD_VIDEO/CLASSROOM_LOG',
    asset_id BIGINT NULL
        COMMENT '业务媒体编号；新建上传在编号生成前可为空',
    subject_user_id INT NOT NULL
        COMMENT '被拍摄/记录学生用户编号，不保存姓名或证件材料',
    consent_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/GRANTED/WITHDRAWN',
    policy_version VARCHAR(32) NOT NULL,
    evidence_digest CHAR(64) NULL
        COMMENT '线下授权材料不可逆摘要；不得写入原件或证件号',
    granted_at DATETIME NULL,
    withdrawn_at DATETIME NULL,
    recorded_by_user_id INT NULL,
    audit_event_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_media_consent_lookup (asset_type, asset_id, subject_user_id, consent_status),
    KEY idx_media_consent_subject_time (subject_user_id, created_at),
    KEY idx_media_consent_audit (audit_event_id),
    CONSTRAINT chk_media_consent_status
        CHECK (consent_status IN ('PENDING', 'GRANTED', 'WITHDRAWN')),
    CONSTRAINT chk_media_consent_subject_positive
        CHECK (subject_user_id > 0),
    CONSTRAINT chk_media_consent_evidence_digest
        CHECK (evidence_digest IS NULL OR evidence_digest REGEXP '^[0-9A-Fa-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='儿童照片、视频、课堂日志的最小公开授权留痕；默认未授权';

-- No UPDATE/DELETE backfill is intentional: existing business rows remain
-- unavailable to public paths until a fresh CLEAN scan and explicit consent
-- are recorded by the application.
