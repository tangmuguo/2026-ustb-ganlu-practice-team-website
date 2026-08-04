-- Message board backend patch.
-- Run after importing the baseline ganlu.sql. This patch does not drop tables
-- and does not delete existing messages or replies.

-- Ensure historical rows reference existing users/messages before adding FKs.
-- Review these result sets first; fix any returned rows manually.
SELECT m.id AS orphan_message_id, m.user_id
FROM message m
LEFT JOIN user u ON u.id = m.user_id
WHERE u.id IS NULL;

SELECT r.id AS orphan_reply_id, r.message_id, r.user_id
FROM reply r
LEFT JOIN message m ON m.id = r.message_id
LEFT JOIN user u ON u.id = r.user_id
WHERE m.id IS NULL OR u.id IS NULL;

ALTER TABLE message
    ADD INDEX idx_message_status_create_id (status, create_time, id);

ALTER TABLE reply
    ADD INDEX idx_reply_message_status_create_id (message_id, status, create_time, id);

-- Add foreign keys only after confirming the orphan checks above return no rows.
-- ON DELETE RESTRICT keeps message/reply audit history and prevents physical
-- user deletion from cascading into historical board content.
ALTER TABLE message
    ADD CONSTRAINT fk_message_user
        FOREIGN KEY (user_id) REFERENCES user(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE reply
    ADD CONSTRAINT fk_reply_message
        FOREIGN KEY (message_id) REFERENCES message(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD CONSTRAINT fk_reply_user
        FOREIGN KEY (user_id) REFERENCES user(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;
