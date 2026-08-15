-- Append-only security audit events, preservation holds and report tickets.
-- Back up and rehearse on a restored MySQL copy before production execution.

CREATE TABLE IF NOT EXISTS audit_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id CHAR(36) NULL,
    occurred_at DATETIME NOT NULL,
    actor_user_id INT NULL,
    actor_role INT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NULL,
    resource_id VARCHAR(128) NULL,
    outcome VARCHAR(16) NOT NULL,
    http_method VARCHAR(12) NULL,
    request_path VARCHAR(512) NULL,
    source_ip VARCHAR(64) NULL,
    target_host VARCHAR(255) NULL,
    target_port INT NULL,
    user_agent VARCHAR(512) NULL,
    reason_code VARCHAR(64) NULL,
    metadata_json VARCHAR(1000) NULL,
    retention_until DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_occurred_at (occurred_at),
    KEY idx_audit_actor_time (actor_user_id, occurred_at),
    KEY idx_audit_resource_time (resource_type, resource_id, occurred_at),
    KEY idx_audit_action_outcome_time (action, outcome, occurred_at),
    KEY idx_audit_retention (retention_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='最小必要操作审计；不得写入密码、Token、Cookie 或完整请求体';

CREATE TABLE IF NOT EXISTS audit_preservation_hold (
    id BIGINT NOT NULL AUTO_INCREMENT,
    audit_event_id BIGINT NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    created_by_user_id INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at DATETIME NULL,
    released_by_user_id INT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_hold_active (audit_event_id, released_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='案件/投诉处理期间的审计日志保全标记';

CREATE TABLE IF NOT EXISTS content_report (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reporter_user_id INT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id INT NOT NULL,
    category VARCHAR(64) NOT NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    handled_by_user_id INT NULL,
    handled_at DATETIME NULL,
    resolution_code VARCHAR(64) NULL,
    resolution_note VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_report_status_time (status, created_at),
    KEY idx_report_target (target_type, target_id, created_at),
    KEY idx_reporter_time (reporter_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容举报/投诉工单；联系人如需留存须另行加密处理';
