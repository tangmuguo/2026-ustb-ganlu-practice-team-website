package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.MessageEntity;
import com.vihu.ganlu.entitys.ReplyEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.message.ContentReviewRequest;
import com.vihu.ganlu.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class MessageMapperIntegrationTests {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MessageMapper messageMapper;
    @Autowired
    private ReplyMapper replyMapper;
    @Autowired
    private MessageServiceImpl messageService;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS reply");
        jdbcTemplate.execute("DROP TABLE IF EXISTS message");
        jdbcTemplate.execute("DROP TABLE IF EXISTS content_moderation_history");
        jdbcTemplate.execute("DROP TABLE IF EXISTS audit_event");
        jdbcTemplate.execute("DROP TABLE IF EXISTS user");
        jdbcTemplate.execute("CREATE TABLE user (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, " +
                "username VARCHAR(100), " +
                "password VARCHAR(100), " +
                "image_url VARCHAR(255), " +
                "teamname VARCHAR(100), " +
                "helplocation VARCHAR(100), " +
                "helpschool VARCHAR(100), " +
                "realname VARCHAR(100), " +
                "belongschool VARCHAR(100), " +
                "grade VARCHAR(50), " +
                "phone VARCHAR(50), " +
                "level INT, " +
                "display_name VARCHAR(64))");
        jdbcTemplate.execute("CREATE TABLE message (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, " +
                "user_id INT NOT NULL, " +
                "content VARCHAR(500) NOT NULL, " +
                "create_time TIMESTAMP NOT NULL, " +
                "update_time TIMESTAMP NOT NULL, " +
                "status TINYINT NOT NULL, " +
                "content_status VARCHAR(16) NOT NULL, " +
                "reviewed_by_user_id INT, reviewed_at TIMESTAMP, review_reason_code VARCHAR(64), review_note VARCHAR(500), " +
                "removed_by_user_id INT, removed_at TIMESTAMP, removal_reason_code VARCHAR(64))");
        jdbcTemplate.execute("CREATE TABLE reply (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, " +
                "message_id INT NOT NULL, " +
                "user_id INT NOT NULL, " +
                "content VARCHAR(300) NOT NULL, " +
                "create_time TIMESTAMP NOT NULL, " +
                "update_time TIMESTAMP NOT NULL, " +
                "status TINYINT NOT NULL, " +
                "content_status VARCHAR(16) NOT NULL, " +
                "reviewed_by_user_id INT, reviewed_at TIMESTAMP, review_reason_code VARCHAR(64), review_note VARCHAR(500), " +
                "removed_by_user_id INT, removed_at TIMESTAMP, removal_reason_code VARCHAR(64))");
        jdbcTemplate.execute("CREATE TABLE content_moderation_history (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "content_type VARCHAR(16) NOT NULL, content_id INT NOT NULL, " +
                "previous_status VARCHAR(16), new_status VARCHAR(16) NOT NULL, " +
                "actor_user_id INT NOT NULL, reason_code VARCHAR(64) NOT NULL, note VARCHAR(500), " +
                "created_at TIMESTAMP NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE audit_event (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, request_id VARCHAR(36), occurred_at TIMESTAMP NOT NULL, " +
                "actor_user_id INT, actor_role INT, action VARCHAR(64) NOT NULL, resource_type VARCHAR(64), " +
                "resource_id VARCHAR(128), outcome VARCHAR(16) NOT NULL, http_method VARCHAR(12), " +
                "request_path VARCHAR(512), source_ip VARCHAR(64), target_host VARCHAR(255), target_port INT, " +
                "user_agent VARCHAR(512), reason_code VARCHAR(64), metadata_json VARCHAR(1000), " +
                "retention_until TIMESTAMP NOT NULL, created_at TIMESTAMP NOT NULL)");
        jdbcTemplate.update("INSERT INTO user(id, username, teamname, level) VALUES (1, 'admin', 'team-a', 0)");
        jdbcTemplate.update("INSERT INTO user(id, username, teamname, level) VALUES (2, 'student', 'team-b', 2)");
    }

    @Test
    void mapperLoadsXmlAndExecutesPagingBatchRepliesAndLogicalDelete() {
        insertElevenMessagesWithSameTimestamp();
        jdbcTemplate.update("INSERT INTO reply(id, message_id, user_id, content, create_time, update_time, status, content_status) " +
                "VALUES (1, 11, 2, 'old reply', '2026-01-01 11:00:00', '2026-01-01 11:00:00', 1, 'APPROVED')");
        jdbcTemplate.update("INSERT INTO reply(id, message_id, user_id, content, create_time, update_time, status, content_status) " +
                "VALUES (2, 11, 2, 'new reply', '2026-01-01 11:00:00', '2026-01-01 11:00:00', 1, 'APPROVED')");
        jdbcTemplate.update("INSERT INTO reply(id, message_id, user_id, content, create_time, update_time, status, content_status) " +
                "VALUES (3, 11, 2, 'deleted reply', '2026-01-01 11:00:00', '2026-01-01 11:00:00', 0, 'REMOVED')");
        jdbcTemplate.update("INSERT INTO reply(id, message_id, user_id, content, create_time, update_time, status, content_status) " +
                "VALUES (4, 10, 1, 'another reply', '2026-01-01 11:00:00', '2026-01-01 11:00:00', 1, 'APPROVED')");

        List<MessageEntity> firstPage = messageMapper.selectMessages(0, 10);
        List<MessageEntity> secondPage = messageMapper.selectMessages(10, 10);

        assertEquals(10, firstPage.size());
        assertEquals(11, firstPage.get(0).getId());
        assertEquals(10, firstPage.get(1).getId());
        assertEquals(1, secondPage.size());
        assertEquals(1, secondPage.get(0).getId());
        assertEquals(11, messageMapper.countMessages());

        List<ReplyEntity> replies = replyMapper.selectRepliesByMessageIds(Arrays.asList(11, 10));
        assertEquals(3, replies.size());
        assertEquals(10, replies.get(0).getMessageId());
        assertEquals(11, replies.get(1).getMessageId());
        assertEquals(2, replies.get(1).getId());
        assertEquals(1, replies.get(2).getId());

        assertEquals(1, messageMapper.removeMessageForActor(11, 1, "MODERATION_REMOVE"));
        assertNull(messageMapper.selectMessageById(11));
        assertEquals(10, messageMapper.countMessages());
        assertFalse(messageMapper.selectMessages(0, 10).stream()
                .anyMatch(message -> Integer.valueOf(11).equals(message.getId())));
    }

    @Test
    void deletedUserDoesNotHideHistoricalMessage() {
        jdbcTemplate.update("INSERT INTO user(id, username, teamname, level) VALUES (9, 'gone', 'old-team', 2)");
        jdbcTemplate.update("INSERT INTO message(id, user_id, content, create_time, update_time, status, content_status) " +
                "VALUES (101, 9, 'history', '2026-01-01 12:00:00', '2026-01-01 12:00:00', 1, 'APPROVED')");

        jdbcTemplate.update("DELETE FROM user WHERE id = 9");

        Map<String, Object> page = messageService.getMessages(1, 10);
        @SuppressWarnings("unchecked")
        List<MessageEntity> messages = (List<MessageEntity>) page.get("messages");

        assertEquals(1, messages.size());
        assertEquals(101, messages.get(0).getId());
        assertEquals("用户#9", messages.get(0).getDisplayName());
        assertTrue(messages.get(0).getReplies().isEmpty());
    }

    @Test
    void ownerRemovalLeavesOtherAuthorsReplyStateAndEvidenceUntouched() {
        jdbcTemplate.update("INSERT INTO message(id, user_id, content, create_time, update_time, status, content_status) " +
                "VALUES (201, 2, 'parent', '2026-01-01 12:00:00', '2026-01-01 12:00:00', 1, 'APPROVED')");
        jdbcTemplate.update("INSERT INTO reply(id, message_id, user_id, content, create_time, update_time, status, content_status) " +
                "VALUES (301, 201, 1, 'admin reply', '2026-01-01 12:00:00', '2026-01-01 12:00:00', 1, 'APPROVED')");
        jdbcTemplate.update("INSERT INTO reply(id, message_id, user_id, content, create_time, update_time, status, content_status) " +
                "VALUES (302, 201, 2, 'author reply', '2026-01-01 12:00:00', '2026-01-01 12:00:00', 1, 'APPROVED')");

        messageService.deleteMessage(201, 2);

        Map<String, Object> otherReply = jdbcTemplate.queryForMap(
                "SELECT status, content_status, removed_by_user_id, removal_reason_code FROM reply WHERE id = 301");
        assertEquals(1, ((Number) otherReply.get("status")).intValue());
        assertEquals("APPROVED", otherReply.get("content_status"));
        assertNull(otherReply.get("removed_by_user_id"));
        assertNull(otherReply.get("removal_reason_code"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM content_moderation_history WHERE content_type = 'MESSAGE' AND content_id = 201",
                Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM content_moderation_history WHERE content_type = 'REPLY' AND content_id IN (301, 302)",
                Integer.class));
    }

    @Test
    void teamAccountCannotRemoveAnotherUsersParentOrCascadeReplies() {
        jdbcTemplate.update("INSERT INTO user(id, username, teamname, level) VALUES (9, 'other', 'team-c', 2)");
        jdbcTemplate.update("INSERT INTO user(id, username, teamname, level) VALUES (3, 'team', 'team-b', 1)");
        jdbcTemplate.update("INSERT INTO message(id, user_id, content, create_time, update_time, status, content_status) " +
                "VALUES (202, 9, 'parent', '2026-01-01 12:00:00', '2026-01-01 12:00:00', 1, 'APPROVED')");
        jdbcTemplate.update("INSERT INTO reply(id, message_id, user_id, content, create_time, update_time, status, content_status) " +
                "VALUES (303, 202, 2, 'reply', '2026-01-01 12:00:00', '2026-01-01 12:00:00', 1, 'APPROVED')");

        UserEntity team = new UserEntity();
        team.setId(3);
        team.setLevel(1);
        org.junit.jupiter.api.Assertions.assertThrows(SecurityException.class,
                () -> messageService.deleteMessage(202, 3));
        // There is no bulk reply-removal mapper path; only the administrator-only
        // service cascade can transition replies.
        assertEquals(1, jdbcTemplate.queryForObject("SELECT status FROM reply WHERE id = 303", Number.class).intValue());
        assertEquals("APPROVED", jdbcTemplate.queryForObject(
                "SELECT content_status FROM reply WHERE id = 303", String.class));
    }

    @Test
    void administratorParentDispositionRecordsEachCascadedReply() {
        jdbcTemplate.update("INSERT INTO message(id, user_id, content, create_time, update_time, status, content_status) " +
                "VALUES (203, 2, 'parent', '2026-01-01 12:00:00', '2026-01-01 12:00:00', 1, 'APPROVED')");
        jdbcTemplate.update("INSERT INTO reply(id, message_id, user_id, content, create_time, update_time, status, content_status) " +
                "VALUES (304, 203, 2, 'reply one', '2026-01-01 12:00:00', '2026-01-01 12:00:00', 1, 'APPROVED')");
        jdbcTemplate.update("INSERT INTO reply(id, message_id, user_id, content, create_time, update_time, status, content_status) " +
                "VALUES (305, 203, 2, 'reply two', '2026-01-01 12:00:00', '2026-01-01 12:00:00', 1, 'APPROVED')");

        ContentReviewRequest review = new ContentReviewRequest();
        review.setContentType("MESSAGE");
        review.setContentId(203);
        review.setDecision("REMOVED");
        review.setReasonCode("PARENT_POLICY");
        review.setNote("parent policy breach");
        UserEntity admin = new UserEntity();
        admin.setId(1);
        admin.setLevel(0);

        messageService.reviewContent(review, admin);

        assertEquals(0, jdbcTemplate.queryForObject("SELECT status FROM reply WHERE id = 304", Number.class).intValue());
        assertEquals("REMOVED", jdbcTemplate.queryForObject(
                "SELECT content_status FROM reply WHERE id = 304", String.class));
        assertEquals("PARENT_POLICY", jdbcTemplate.queryForObject(
                "SELECT removal_reason_code FROM reply WHERE id = 304", String.class));
        assertEquals("PARENT_POLICY", jdbcTemplate.queryForObject(
                "SELECT review_reason_code FROM reply WHERE id = 304", String.class));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM content_moderation_history WHERE content_type = 'REPLY' AND content_id IN (304, 305)",
                Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE action = 'REPLY_REMOVE_CASCADE' AND resource_type = 'REPLY'",
                Integer.class));
    }

    @Test
    void duplicatePreflightOnlyMatchesTheSameRecentContentAndReplyParent() {
        long now = System.currentTimeMillis();
        Timestamp recent = new Timestamp(now - 1_000L);
        Timestamp expired = new Timestamp(now - 15_000L);
        Timestamp queryStart = new Timestamp(now - 10_000L);
        jdbcTemplate.update("INSERT INTO message(id, user_id, content, create_time, update_time, status, content_status) "
                        + "VALUES (401, 1, '近期重复内容', ?, ?, 1, 'APPROVED')",
                recent, recent);
        jdbcTemplate.update("INSERT INTO message(id, user_id, content, create_time, update_time, status, content_status) "
                        + "VALUES (402, 1, '近期重复内容', ?, ?, 1, 'APPROVED')",
                expired, expired);
        jdbcTemplate.update("INSERT INTO reply(id, message_id, user_id, content, create_time, update_time, status, content_status) "
                        + "VALUES (501, 401, 1, '近期回复', ?, ?, 1, 'APPROVED')",
                recent, recent);
        jdbcTemplate.update("INSERT INTO reply(id, message_id, user_id, content, create_time, update_time, status, content_status) "
                        + "VALUES (502, 402, 1, '近期回复', ?, ?, 1, 'APPROVED')",
                recent, recent);

        assertEquals(1, messageMapper.countRecentDuplicate(1, "近期重复内容", new Date(queryStart.getTime())));
        assertEquals(1, replyMapper.countRecentDuplicate(1, 401, "近期回复", new Date(queryStart.getTime())));
        assertEquals(1, replyMapper.countRecentDuplicate(1, 402, "近期回复", new Date(queryStart.getTime())));
        assertEquals(0, replyMapper.countRecentDuplicate(1, 403, "近期回复", new Date(queryStart.getTime())));

        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM message", Integer.class).intValue());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> messageService.addMessage("近期重复内容", 1));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM message WHERE content = '近期重复内容' AND user_id = 1", Integer.class).intValue());
    }

    private void insertElevenMessagesWithSameTimestamp() {
        for (int id = 1; id <= 11; id++) {
            jdbcTemplate.update("INSERT INTO message(id, user_id, content, create_time, update_time, status, content_status) " +
                    "VALUES (?, 1, ?, '2026-01-01 10:00:00', '2026-01-01 10:00:00', 1, 'APPROVED')", id, "message-" + id);
        }
    }
}

