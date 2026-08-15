-- Privacy-rights request tickets (correction, deletion and consent withdrawal).
-- Run only on a verified MySQL backup copy first.  This patch is additive and
-- idempotent; it never deletes account, content or file data and does not
-- perform a physical erasure on approval.

CREATE TABLE IF NOT EXISTS privacy_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    requester_user_id INT NOT NULL,
    request_type VARCHAR(32) NOT NULL COMMENT 'CORRECTION/DELETION/WITHDRAW_CONSENT',
    consent_type VARCHAR(32) NULL COMMENT 'GUARDIAN/PRIVACY; only used for withdrawal',
    scope_code VARCHAR(64) NOT NULL COMMENT 'Minimal affected data scope, not a data dump',
    description VARCHAR(2000) NOT NULL COMMENT 'Requester explanation; never store passwords, tokens or identity documents',
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/PROCESSING/APPROVED/REJECTED',
    handled_by_user_id INT NULL,
    handled_at DATETIME NULL,
    decision_code VARCHAR(64) NULL,
    decision_reason VARCHAR(1000) NULL COMMENT 'Mandatory administrator reason for every handling transition',
    retention_decision VARCHAR(32) NULL COMMENT 'Deletion requests remain ticket-only; preserve/review decision is recorded here',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_privacy_requester_time (requester_user_id, created_at, id),
    KEY idx_privacy_status_time (status, created_at, id),
    KEY idx_privacy_type_consent (requester_user_id, request_type, consent_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='个人隐私权利工单；审批不等于未经保全判断的物理删除';
