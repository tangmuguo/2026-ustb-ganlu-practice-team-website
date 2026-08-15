-- Content moderation status model. Additive only: historical message/reply
-- rows become PENDING by default and require human review before public display.

DROP PROCEDURE IF EXISTS ganlu_add_column_if_missing;
DROP PROCEDURE IF EXISTS ganlu_add_index_if_missing;

DELIMITER $$
CREATE PROCEDURE ganlu_add_column_if_missing(
    IN table_name_value VARCHAR(64), IN column_name_value VARCHAR(64), IN alter_sql_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = table_name_value AND COLUMN_NAME = column_name_value
    ) THEN
        SET @ganlu_sql = alter_sql_value;
        PREPARE ganlu_stmt FROM @ganlu_sql;
        EXECUTE ganlu_stmt;
        DEALLOCATE PREPARE ganlu_stmt;
    END IF;
END$$
CREATE PROCEDURE ganlu_add_index_if_missing(
    IN table_name_value VARCHAR(64), IN index_name_value VARCHAR(64), IN alter_sql_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = table_name_value AND INDEX_NAME = index_name_value
    ) THEN
        SET @ganlu_sql = alter_sql_value;
        PREPARE ganlu_stmt FROM @ganlu_sql;
        EXECUTE ganlu_stmt;
        DEALLOCATE PREPARE ganlu_stmt;
    END IF;
END$$
DELIMITER ;

CALL ganlu_add_column_if_missing('message', 'content_status',
  'ALTER TABLE message ADD COLUMN content_status VARCHAR(16) NOT NULL DEFAULT ''PENDING'' COMMENT ''PENDING/APPROVED/REJECTED/REMOVED'' AFTER status');
CALL ganlu_add_column_if_missing('message', 'reviewed_by_user_id',
  'ALTER TABLE message ADD COLUMN reviewed_by_user_id INT NULL AFTER content_status');
CALL ganlu_add_column_if_missing('message', 'reviewed_at',
  'ALTER TABLE message ADD COLUMN reviewed_at DATETIME NULL AFTER reviewed_by_user_id');
CALL ganlu_add_column_if_missing('message', 'review_reason_code',
  'ALTER TABLE message ADD COLUMN review_reason_code VARCHAR(64) NULL AFTER reviewed_at');
CALL ganlu_add_column_if_missing('message', 'review_note',
  'ALTER TABLE message ADD COLUMN review_note VARCHAR(500) NULL AFTER review_reason_code');
CALL ganlu_add_column_if_missing('message', 'removed_by_user_id',
  'ALTER TABLE message ADD COLUMN removed_by_user_id INT NULL AFTER review_note');
CALL ganlu_add_column_if_missing('message', 'removed_at',
  'ALTER TABLE message ADD COLUMN removed_at DATETIME NULL AFTER removed_by_user_id');
CALL ganlu_add_column_if_missing('message', 'removal_reason_code',
  'ALTER TABLE message ADD COLUMN removal_reason_code VARCHAR(64) NULL AFTER removed_at');

CALL ganlu_add_column_if_missing('reply', 'content_status',
  'ALTER TABLE reply ADD COLUMN content_status VARCHAR(16) NOT NULL DEFAULT ''PENDING'' COMMENT ''PENDING/APPROVED/REJECTED/REMOVED'' AFTER status');
CALL ganlu_add_column_if_missing('reply', 'reviewed_by_user_id',
  'ALTER TABLE reply ADD COLUMN reviewed_by_user_id INT NULL AFTER content_status');
CALL ganlu_add_column_if_missing('reply', 'reviewed_at',
  'ALTER TABLE reply ADD COLUMN reviewed_at DATETIME NULL AFTER reviewed_by_user_id');
CALL ganlu_add_column_if_missing('reply', 'review_reason_code',
  'ALTER TABLE reply ADD COLUMN review_reason_code VARCHAR(64) NULL AFTER reviewed_at');
CALL ganlu_add_column_if_missing('reply', 'review_note',
  'ALTER TABLE reply ADD COLUMN review_note VARCHAR(500) NULL AFTER review_reason_code');
CALL ganlu_add_column_if_missing('reply', 'removed_by_user_id',
  'ALTER TABLE reply ADD COLUMN removed_by_user_id INT NULL AFTER review_note');
CALL ganlu_add_column_if_missing('reply', 'removed_at',
  'ALTER TABLE reply ADD COLUMN removed_at DATETIME NULL AFTER removed_by_user_id');
CALL ganlu_add_column_if_missing('reply', 'removal_reason_code',
  'ALTER TABLE reply ADD COLUMN removal_reason_code VARCHAR(64) NULL AFTER removed_at');

CALL ganlu_add_index_if_missing('message', 'idx_message_content_status_time',
  'ALTER TABLE message ADD INDEX idx_message_content_status_time (content_status, create_time, id)');
CALL ganlu_add_index_if_missing('reply', 'idx_reply_content_status_time',
  'ALTER TABLE reply ADD INDEX idx_reply_content_status_time (message_id, content_status, create_time, id)');
-- The 10-second duplicate-submission guard searches only the current user's
-- recent rows.  These indexes keep that preflight bounded without indexing
-- the content body itself.
CALL ganlu_add_index_if_missing('message', 'idx_message_user_recent_submission',
  'ALTER TABLE message ADD INDEX idx_message_user_recent_submission (user_id, create_time, id)');
CALL ganlu_add_index_if_missing('reply', 'idx_reply_user_recent_submission',
  'ALTER TABLE reply ADD INDEX idx_reply_user_recent_submission (user_id, message_id, create_time, id)');

CREATE TABLE IF NOT EXISTS content_moderation_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content_type VARCHAR(16) NOT NULL,
    content_id INT NOT NULL,
    previous_status VARCHAR(16) NULL,
    new_status VARCHAR(16) NOT NULL,
    actor_user_id INT NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_content_history_target_time (content_type, content_id, created_at),
    KEY idx_content_history_actor_time (actor_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='留言与回复审核/处置历史；原文保留在主表';

DROP PROCEDURE IF EXISTS ganlu_add_column_if_missing;
DROP PROCEDURE IF EXISTS ganlu_add_index_if_missing;
