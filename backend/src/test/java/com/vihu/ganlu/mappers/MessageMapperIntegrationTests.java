package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.MessageEntity;
import com.vihu.ganlu.entitys.ReplyEntity;
import com.vihu.ganlu.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
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
                "level INT)");
        jdbcTemplate.execute("CREATE TABLE message (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, " +
                "user_id INT NOT NULL, " +
                "content VARCHAR(500) NOT NULL, " +
                "create_time TIMESTAMP NOT NULL, " +
                "update_time TIMESTAMP NOT NULL, " +
                "status TINYINT NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE reply (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, " +
                "message_id INT NOT NULL, " +
                "user_id INT NOT NULL, " +
                "content VARCHAR(300) NOT NULL, " +
                "create_time TIMESTAMP NOT NULL, " +
                "update_time TIMESTAMP NOT NULL, " +
                "status TINYINT NOT NULL)");
        jdbcTemplate.update("INSERT INTO user(id, username, teamname, level) VALUES (1, 'admin', 'team-a', 0)");
        jdbcTemplate.update("INSERT INTO user(id, username, teamname, level) VALUES (2, 'student', 'team-b', 2)");
    }

    @Test
    void mapperLoadsXmlAndExecutesPagingBatchRepliesAndLogicalDelete() {
        insertElevenMessagesWithSameTimestamp();
        jdbcTemplate.update("INSERT INTO reply(id, message_id, user_id, content, create_time, update_time, status) " +
                "VALUES (1, 11, 2, 'old reply', '2026-01-01 11:00:00', '2026-01-01 11:00:00', 1)");
        jdbcTemplate.update("INSERT INTO reply(id, message_id, user_id, content, create_time, update_time, status) " +
                "VALUES (2, 11, 2, 'new reply', '2026-01-01 11:00:00', '2026-01-01 11:00:00', 1)");
        jdbcTemplate.update("INSERT INTO reply(id, message_id, user_id, content, create_time, update_time, status) " +
                "VALUES (3, 11, 2, 'deleted reply', '2026-01-01 11:00:00', '2026-01-01 11:00:00', 0)");
        jdbcTemplate.update("INSERT INTO reply(id, message_id, user_id, content, create_time, update_time, status) " +
                "VALUES (4, 10, 1, 'another reply', '2026-01-01 11:00:00', '2026-01-01 11:00:00', 1)");

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

        assertEquals(1, messageMapper.deleteMessage(11));
        assertNull(messageMapper.selectMessageById(11));
        assertEquals(10, messageMapper.countMessages());
        assertFalse(messageMapper.selectMessages(0, 10).stream()
                .anyMatch(message -> Integer.valueOf(11).equals(message.getId())));
    }

    @Test
    void deletedUserDoesNotHideHistoricalMessage() {
        jdbcTemplate.update("INSERT INTO user(id, username, teamname, level) VALUES (9, 'gone', 'old-team', 2)");
        jdbcTemplate.update("INSERT INTO message(id, user_id, content, create_time, update_time, status) " +
                "VALUES (101, 9, 'history', '2026-01-01 12:00:00', '2026-01-01 12:00:00', 1)");

        jdbcTemplate.update("DELETE FROM user WHERE id = 9");

        Map<String, Object> page = messageService.getMessages(1, 10);
        @SuppressWarnings("unchecked")
        List<MessageEntity> messages = (List<MessageEntity>) page.get("messages");

        assertEquals(1, messages.size());
        assertEquals(101, messages.get(0).getId());
        assertEquals("用户#9", messages.get(0).getUsername());
        assertTrue(messages.get(0).getReplies().isEmpty());
    }

    private void insertElevenMessagesWithSameTimestamp() {
        for (int id = 1; id <= 11; id++) {
            jdbcTemplate.update("INSERT INTO message(id, user_id, content, create_time, update_time, status) " +
                    "VALUES (?, 1, ?, '2026-01-01 10:00:00', '2026-01-01 10:00:00', 1)", id, "message-" + id);
        }
    }
}
