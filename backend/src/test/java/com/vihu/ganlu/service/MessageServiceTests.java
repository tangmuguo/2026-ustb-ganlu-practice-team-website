package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.MessageEntity;
import com.vihu.ganlu.entitys.ReplyEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.message.MessageCreateRequest;
import com.vihu.ganlu.entitys.message.ReplyCreateRequest;
import com.vihu.ganlu.mappers.MessageMapper;
import com.vihu.ganlu.mappers.ReplyMapper;
import com.vihu.ganlu.mappers.UserMapper;
import com.vihu.ganlu.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageServiceTests {
    private MessageMapper messageMapper;
    private ReplyMapper replyMapper;
    private UserMapper userMapper;
    private MessageServiceImpl service;

    @BeforeEach
    void setUp() {
        messageMapper = mock(MessageMapper.class);
        replyMapper = mock(ReplyMapper.class);
        userMapper = mock(UserMapper.class);
        service = new MessageServiceImpl(messageMapper, replyMapper, userMapper);
    }

    @Test
    void level0Level1Level2CanCreateMessage() {
        for (int level = 0; level <= 2; level++) {
            when(userMapper.findUserById(level + 1)).thenReturn(user(level + 1, level));
            MessageCreateRequest request = new MessageCreateRequest();
            request.setContent("  hello  ");

            MessageEntity created = service.addMessage(request, level + 1);

            assertEquals(level + 1, created.getUserId());
            assertEquals("hello", created.getContent());
        }
        verify(messageMapper, org.mockito.Mockito.times(3)).insertMessage(any(MessageEntity.class));
    }

    @Test
    void level0Level1Level2CanCreateReply() {
        when(messageMapper.selectMessageById(7)).thenReturn(message(7));
        for (int level = 0; level <= 2; level++) {
            when(userMapper.findUserById(level + 1)).thenReturn(user(level + 1, level));
            ReplyCreateRequest request = new ReplyCreateRequest();
            request.setMessageId(7);
            request.setContent("  reply  ");

            ReplyEntity created = service.addReply(request, level + 1);

            assertEquals(level + 1, created.getUserId());
            assertEquals(7, created.getMessageId());
            assertEquals("reply", created.getContent());
        }
        verify(replyMapper, org.mockito.Mockito.times(3)).insertReply(any(ReplyEntity.class));
    }

    @Test
    void createMessageRejectsBlankAndTooLongContent() {
        when(userMapper.findUserById(1)).thenReturn(user(1, 0));
        MessageCreateRequest blank = new MessageCreateRequest();
        blank.setContent("   ");
        assertThrows(IllegalArgumentException.class, () -> service.addMessage(blank, 1));

        MessageCreateRequest tooLong = new MessageCreateRequest();
        tooLong.setContent(repeat("a", 501));
        assertThrows(IllegalArgumentException.class, () -> service.addMessage(tooLong, 1));
        verify(messageMapper, never()).insertMessage(any(MessageEntity.class));
    }

    @Test
    void listUsesBatchReplyQueryAndReturnsPageInfo() {
        MessageEntity first = message(11);
        MessageEntity second = message(10);
        ReplyEntity reply = reply(21, 11);
        first.setUsername(null);
        reply.setUsername(null);
        when(messageMapper.selectMessages(0, 10)).thenReturn(Arrays.asList(first, second));
        when(replyMapper.selectRepliesByMessageIds(Arrays.asList(11, 10))).thenReturn(Collections.singletonList(reply));
        when(messageMapper.countMessages()).thenReturn(11);

        Map<String, Object> result = service.getMessages(1, 10);

        assertEquals(11, result.get("total"));
        assertEquals(1, result.get("page"));
        assertEquals(10, result.get("pageSize"));
        assertEquals(1, first.getReplies().size());
        assertEquals(0, second.getReplies().size());
        assertEquals("用户#1", first.getUsername());
        assertEquals("用户#2", reply.getUsername());
        verify(replyMapper).selectRepliesByMessageIds(Arrays.asList(11, 10));
        verify(replyMapper, never()).selectRepliesByMessageId(any());
        verify(userMapper, never()).findUserById(eq(11));
    }

    @Test
    void listRejectsIllegalPageParams() {
        assertThrows(IllegalArgumentException.class, () -> service.getMessages(0, 10));
        assertThrows(IllegalArgumentException.class, () -> service.getMessages(1, 1000));
        assertThrows(IllegalArgumentException.class, () -> service.getMessages(Integer.MAX_VALUE, 50));
        verify(messageMapper, never()).selectMessages(any(Integer.class), any(Integer.class));
    }

    @Test
    void listSecondPageUsesStableOffset() {
        when(messageMapper.selectMessages(10, 10)).thenReturn(Collections.singletonList(message(1)));
        when(replyMapper.selectRepliesByMessageIds(Collections.singletonList(1))).thenReturn(Collections.emptyList());
        when(messageMapper.countMessages()).thenReturn(11);

        Map<String, Object> result = service.getMessages(2, 10);

        assertEquals(11, result.get("total"));
        assertEquals(2, result.get("page"));
        verify(messageMapper).selectMessages(10, 10);
    }

    @Test
    void listAllowsMaxBusinessOffset() {
        when(messageMapper.selectMessages(10000, 50)).thenReturn(Collections.singletonList(message(1)));
        when(replyMapper.selectRepliesByMessageIds(Collections.singletonList(1))).thenReturn(Collections.emptyList());
        when(messageMapper.countMessages()).thenReturn(10001);

        Map<String, Object> result = service.getMessages(201, 50);

        assertEquals(201, result.get("page"));
        verify(messageMapper).selectMessages(10000, 50);
    }

    @Test
    void listRejectsPageBeyondBusinessOffsetBeforeQueryingMessages() {
        assertThrows(IllegalArgumentException.class, () -> service.getMessages(202, 50));

        verify(messageMapper, never()).selectMessages(any(Integer.class), any(Integer.class));
    }

    @Test
    void replyRejectsDeletedOrMissingMessage() {
        when(userMapper.findUserById(3)).thenReturn(user(3, 2));
        ReplyCreateRequest request = new ReplyCreateRequest();
        request.setMessageId(99);
        request.setContent("reply");
        when(messageMapper.selectMessageById(99)).thenReturn(null);

        assertThrows(NoSuchElementException.class, () -> service.addReply(request, 3));
        verify(replyMapper, never()).insertReply(any(ReplyEntity.class));
    }

    @Test
    void studentCannotDeleteEvenWhenForgingPrivilegedUserIdInBody() {
        when(userMapper.findUserById(3)).thenReturn(user(3, 2));

        assertThrows(SecurityException.class, () -> service.deleteMessage(1, 3));

        verify(messageMapper, never()).deleteMessage(any());
    }

    @Test
    void adminAndTeamCanDeleteMessageAndReply() {
        when(userMapper.findUserById(1)).thenReturn(user(1, 0));
        when(userMapper.findUserById(2)).thenReturn(user(2, 1));
        when(messageMapper.selectMessageById(4)).thenReturn(message(4));
        when(replyMapper.selectReplyById(5)).thenReturn(reply(5, 1));

        service.deleteMessage(4, 1);
        service.deleteMessage(4, 2);
        service.deleteReply(5, 1);
        service.deleteReply(5, 2);

        verify(messageMapper, org.mockito.Mockito.times(2)).deleteMessage(4);
        verify(replyMapper, org.mockito.Mockito.times(2)).deleteReply(5);
    }

    private UserEntity user(int id, int level) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setLevel(level);
        return user;
    }

    private MessageEntity message(int id) {
        MessageEntity message = new MessageEntity();
        message.setId(id);
        message.setUserId(1);
        message.setStatus(true);
        return message;
    }

    private ReplyEntity reply(int id, int messageId) {
        ReplyEntity reply = new ReplyEntity();
        reply.setId(id);
        reply.setMessageId(messageId);
        reply.setUserId(2);
        reply.setStatus(true);
        return reply;
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}

