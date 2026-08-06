-- Message board backend patch.
-- Run after importing the baseline ganlu.sql. This patch does not drop tables
-- and does not delete existing messages, replies, or users.
--
-- This patch intentionally does not add message.user_id -> user.id or
-- reply.user_id -> user.id foreign keys. The current shared user module
-- physically deletes users with DELETE FROM user; RESTRICT user foreign keys
-- would make existing user deletion fail with a database error. Historical
-- board content keeps the numeric user_id and the service displays 用户#<id>
-- when the user row no longer exists.

-- Optional diagnostics. Rows returned by the first and third queries are
-- supported by the 用户#<id> fallback. Rows returned by the second query mean
-- reply.message_id points to a missing message and must be fixed before adding
-- fk_reply_message.
SELECT m.id AS orphan_message_id, m.user_id
FROM message m
LEFT JOIN user u ON u.id = m.user_id
WHERE u.id IS NULL;

SELECT r.id AS orphan_reply_id, r.message_id
FROM reply r
LEFT JOIN message m ON m.id = r.message_id
WHERE m.id IS NULL;

SELECT r.id AS orphan_reply_id, r.message_id, r.user_id
FROM reply r
LEFT JOIN user u ON u.id = r.user_id
WHERE u.id IS NULL;

-- Idempotent migration helpers for MySQL 8.0. Re-running the whole file skips
-- indexes/constraints that already exist, so a partial previous run can be
-- completed by executing this file again after fixing orphan reply rows.
DROP PROCEDURE IF EXISTS ganlu_add_index_if_missing;
DROP PROCEDURE IF EXISTS ganlu_add_fk_if_missing;

DELIMITER $$

CREATE PROCEDURE ganlu_add_index_if_missing(
    IN table_name_value VARCHAR(64),
    IN index_name_value VARCHAR(64),
    IN alter_sql_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = table_name_value
          AND INDEX_NAME = index_name_value
    ) THEN
        SET @ganlu_sql = alter_sql_value;
        PREPARE ganlu_stmt FROM @ganlu_sql;
        EXECUTE ganlu_stmt;
        DEALLOCATE PREPARE ganlu_stmt;
    END IF;
END$$

CREATE PROCEDURE ganlu_add_fk_if_missing(
    IN table_name_value VARCHAR(64),
    IN constraint_name_value VARCHAR(64),
    IN alter_sql_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.TABLE_CONSTRAINTS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = table_name_value
          AND CONSTRAINT_NAME = constraint_name_value
          AND CONSTRAINT_TYPE = 'FOREIGN KEY'
    ) THEN
        SET @ganlu_sql = alter_sql_value;
        PREPARE ganlu_stmt FROM @ganlu_sql;
        EXECUTE ganlu_stmt;
        DEALLOCATE PREPARE ganlu_stmt;
    END IF;
END$$

DELIMITER ;

CALL ganlu_add_index_if_missing(
    'message',
    'idx_message_status_create_id',
    'ALTER TABLE message ADD INDEX idx_message_status_create_id (status, create_time, id)'
);

CALL ganlu_add_index_if_missing(
    'reply',
    'idx_reply_message_status_create_id',
    'ALTER TABLE reply ADD INDEX idx_reply_message_status_create_id (message_id, status, create_time, id)'
);

-- Add only the reply -> message foreign key after confirming the missing-message
-- diagnostic above returns no rows. Messages are soft-deleted, not physically
-- deleted by the message board API.
CALL ganlu_add_fk_if_missing(
    'reply',
    'fk_reply_message',
    'ALTER TABLE reply ADD CONSTRAINT fk_reply_message FOREIGN KEY (message_id) REFERENCES message(id) ON UPDATE RESTRICT ON DELETE RESTRICT'
);

DROP PROCEDURE IF EXISTS ganlu_add_index_if_missing;
DROP PROCEDURE IF EXISTS ganlu_add_fk_if_missing;