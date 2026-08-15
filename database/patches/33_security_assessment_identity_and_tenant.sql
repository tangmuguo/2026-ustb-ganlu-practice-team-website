-- Security assessment identity, consent and tenant-bound student management.
-- Run only on a verified MySQL backup copy first. This patch is additive and
-- idempotent; it does not mark any historical student as verified or consented.

DROP PROCEDURE IF EXISTS ganlu_add_column_if_missing;
DROP PROCEDURE IF EXISTS ganlu_add_index_if_missing;

DELIMITER $$

CREATE PROCEDURE ganlu_add_column_if_missing(
    IN table_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64),
    IN alter_sql_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = table_name_value
          AND COLUMN_NAME = column_name_value
    ) THEN
        SET @ganlu_sql = alter_sql_value;
        PREPARE ganlu_stmt FROM @ganlu_sql;
        EXECUTE ganlu_stmt;
        DEALLOCATE PREPARE ganlu_stmt;
    END IF;
END$$

CREATE PROCEDURE ganlu_add_index_if_missing(
    IN table_name_value VARCHAR(64),
    IN index_name_value VARCHAR(64),
    IN alter_sql_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
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

DELIMITER ;

CALL ganlu_add_column_if_missing(
    'user', 'display_name',
    'ALTER TABLE user ADD COLUMN display_name VARCHAR(64) NULL COMMENT ''公开展示名称；未设置时使用匿名编号'' AFTER phone'
);
CALL ganlu_add_column_if_missing(
    'user', 'verification_status',
    'ALTER TABLE user ADD COLUMN verification_status VARCHAR(16) NOT NULL DEFAULT ''PENDING'' COMMENT ''PENDING/VERIFIED/REJECTED/SUSPENDED'' AFTER display_name'
);
CALL ganlu_add_column_if_missing(
    'user', 'verification_method',
    'ALTER TABLE user ADD COLUMN verification_method VARCHAR(32) NULL COMMENT ''线下核验方式标识；不保存证件号或证件影像'' AFTER verification_status'
);
CALL ganlu_add_column_if_missing(
    'user', 'verified_at',
    'ALTER TABLE user ADD COLUMN verified_at DATETIME NULL AFTER verification_method'
);
CALL ganlu_add_column_if_missing(
    'user', 'verified_by_user_id',
    'ALTER TABLE user ADD COLUMN verified_by_user_id INT NULL AFTER verified_at'
);
CALL ganlu_add_column_if_missing(
    'user', 'guardian_consent_status',
    'ALTER TABLE user ADD COLUMN guardian_consent_status VARCHAR(16) NOT NULL DEFAULT ''PENDING'' COMMENT ''PENDING/CONSENTED/WITHDRAWN/NOT_REQUIRED'' AFTER verified_by_user_id'
);
CALL ganlu_add_column_if_missing(
    'user', 'guardian_consent_version',
    'ALTER TABLE user ADD COLUMN guardian_consent_version VARCHAR(32) NULL AFTER guardian_consent_status'
);
CALL ganlu_add_column_if_missing(
    'user', 'guardian_consented_at',
    'ALTER TABLE user ADD COLUMN guardian_consented_at DATETIME NULL AFTER guardian_consent_version'
);
CALL ganlu_add_column_if_missing(
    'user', 'privacy_consent_version',
    'ALTER TABLE user ADD COLUMN privacy_consent_version VARCHAR(32) NULL AFTER guardian_consented_at'
);
CALL ganlu_add_column_if_missing(
    'user', 'privacy_consented_at',
    'ALTER TABLE user ADD COLUMN privacy_consented_at DATETIME NULL AFTER privacy_consent_version'
);
CALL ganlu_add_column_if_missing(
    'user', 'session_version',
    'ALTER TABLE user ADD COLUMN session_version INT NOT NULL DEFAULT 0 COMMENT ''JWT 会话版本；退出、改密或停用后递增'' AFTER privacy_consented_at'
);

CREATE TABLE IF NOT EXISTS student_team_assignment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_user_id INT NOT NULL,
    team_id INT NOT NULL,
    assigned_by_user_id INT NOT NULL,
    assigned_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    scope VARCHAR(32) NOT NULL DEFAULT 'MANAGE',
    active_student_id INT GENERATED ALWAYS AS (
        CASE WHEN revoked_at IS NULL THEN student_user_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_assignment_active (active_student_id),
    KEY idx_student_assignment_active (student_user_id, revoked_at),
    KEY idx_team_assignment_active (team_id, revoked_at),
    KEY idx_assignment_actor_time (assigned_by_user_id, assigned_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生与团队的可撤销管理归属；仅一条有效归属';

CREATE TABLE IF NOT EXISTS user_consent_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    consent_type VARCHAR(32) NOT NULL,
    policy_version VARCHAR(32) NOT NULL,
    granted_at DATETIME NULL,
    withdrawn_at DATETIME NULL,
    operator_user_id INT NULL,
    evidence_digest CHAR(64) NULL COMMENT '仅保存线下授权材料的不可逆摘要',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_consent_user_type_time (user_id, consent_type, created_at),
    KEY idx_consent_granted (granted_at, withdrawn_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='隐私与监护人授权留痕；不保存证件影像';

CALL ganlu_add_index_if_missing(
    'user', 'idx_user_verification_status',
    'ALTER TABLE user ADD INDEX idx_user_verification_status (verification_status, guardian_consent_status)'
);

-- Existing level=2 rows deliberately remain PENDING and unassigned. An
-- administrator must validate each child and create its assignment manually.

-- The assignment table is intentionally hardened without rewriting historical
-- rows.  A preflight failure stops the patch and leaves the old data intact so
-- an operator can reconcile it on a verified backup before retrying.
-- No user_id foreign keys are added: the existing revoke-and-delete workflow
-- retains assignment history after a user row is deleted.  RESTRICT would break
-- that workflow and CASCADE would erase evidence.  New rows are instead gated
-- by the Service/Mapper identity and role checks, while positive-ID checks and
-- the team foreign key protect the durable tenant key.
DROP PROCEDURE IF EXISTS ganlu_harden_student_assignment_20260815;

DELIMITER $$

CREATE PROCEDURE ganlu_harden_student_assignment_20260815()
BEGIN
    DECLARE invalid_rows INT DEFAULT 0;
    DECLARE constraint_exists INT DEFAULT 0;

    SELECT COUNT(1)
      INTO invalid_rows
     FROM student_team_assignment sta
      LEFT JOIN team t ON t.id = sta.team_id
     WHERE sta.team_id < 1
        OR sta.student_user_id < 1
        OR sta.assigned_by_user_id < 1
        OR t.id IS NULL;

    IF invalid_rows > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'student_team_assignment 存在无效历史编号；请先核对学生、团队和操作人编号后再加完整性约束';
    END IF;

    SELECT COUNT(1)
      INTO constraint_exists
      FROM information_schema.TABLE_CONSTRAINTS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'student_team_assignment'
       AND CONSTRAINT_NAME = 'chk_student_assignment_team_positive';
    IF constraint_exists = 0 THEN
        ALTER TABLE student_team_assignment
            ADD CONSTRAINT chk_student_assignment_team_positive CHECK (team_id > 0);
    END IF;

    SELECT COUNT(1)
      INTO constraint_exists
      FROM information_schema.TABLE_CONSTRAINTS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'student_team_assignment'
       AND CONSTRAINT_NAME = 'chk_student_assignment_student_positive';
    IF constraint_exists = 0 THEN
        ALTER TABLE student_team_assignment
            ADD CONSTRAINT chk_student_assignment_student_positive CHECK (student_user_id > 0);
    END IF;

    SELECT COUNT(1)
      INTO constraint_exists
      FROM information_schema.TABLE_CONSTRAINTS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'student_team_assignment'
       AND CONSTRAINT_NAME = 'fk_student_assignment_team';
    IF constraint_exists = 0 THEN
        ALTER TABLE student_team_assignment
            ADD CONSTRAINT fk_student_assignment_team
            FOREIGN KEY (team_id) REFERENCES team (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT;
    END IF;

    SELECT COUNT(1)
      INTO constraint_exists
      FROM information_schema.TABLE_CONSTRAINTS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'student_team_assignment'
       AND CONSTRAINT_NAME = 'chk_student_assignment_actor_positive';
    IF constraint_exists = 0 THEN
        ALTER TABLE student_team_assignment
            ADD CONSTRAINT chk_student_assignment_actor_positive CHECK (assigned_by_user_id > 0);
    END IF;
END$$

CALL ganlu_harden_student_assignment_20260815()$$
DROP PROCEDURE IF EXISTS ganlu_harden_student_assignment_20260815$$

DELIMITER ;

DROP PROCEDURE IF EXISTS ganlu_add_column_if_missing;
DROP PROCEDURE IF EXISTS ganlu_add_index_if_missing;
