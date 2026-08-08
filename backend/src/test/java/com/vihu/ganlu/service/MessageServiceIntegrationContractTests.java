package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.MessageEntity;
import com.vihu.ganlu.entitys.ReplyEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.mappers.MessageMapper;
import com.vihu.ganlu.mappers.ReplyMapper;
import com.vihu.ganlu.mappers.UserMapper;
import com.vihu.ganlu.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class MessageServiceIntegrationContractTests {
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
    void loadsRepliesInOneBatch() {
        MessageEntity first = new MessageEntity(); first.setId(1);
        MessageEntity second = new MessageEntity(); second.setId(2);
        ReplyEntity reply = new ReplyEntity(); reply.setId(10); reply.setMessageId(2);
        when(messageMapper.selectMessages(0, 10)).thenReturn(Arrays.asList(first, second));
        when(replyMapper.selectRepliesByMessageIds(anyList())).thenReturn(Arrays.asList(reply));
        when(messageMapper.countMessages()).thenReturn(2);

        Map<String, Object> result = service.getMessages(1, 10);

        assertEquals(2, result.get("total"));
        assertTrue(first.getReplies().isEmpty());
        assertEquals(1, second.getReplies().size());
        verify(replyMapper, times(1)).selectRepliesByMessageIds(Arrays.asList(1, 2));
        verify(replyMapper, never()).selectRepliesByMessageId(anyInt());
    }

    @Test
    void studentCanPublishButCannotDelete() {
        UserEntity student = new UserEntity(); student.setId(3); student.setLevel(2);
        when(userMapper.findUserById(3)).thenReturn(student);
        when(messageMapper.insertMessage(any())).thenReturn(1);

        assertEquals("一条合规留言", service.addMessage("一条合规留言", 3).getContent());
        assertThrows(SecurityException.class, () -> service.deleteMessage(1, 3));
    }

    @Test
    void rejectsOverlongMessage() {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 501; i++) content.append('字');
        assertThrows(IllegalArgumentException.class, () -> service.addMessage(content.toString(), 1));
        verify(messageMapper, never()).insertMessage(any());
    }
}
