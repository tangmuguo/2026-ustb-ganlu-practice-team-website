package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.MessageEntity;
import com.vihu.ganlu.entitys.ReplyEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.ContentModerationHistoryEntity;
import com.vihu.ganlu.entitys.message.ContentReviewRequest;
import com.vihu.ganlu.entitys.message.MessageCreateRequest;
import com.vihu.ganlu.entitys.message.ReplyCreateRequest;
import com.vihu.ganlu.mappers.ContentModerationHistoryMapper;
import com.vihu.ganlu.mappers.MessageMapper;
import com.vihu.ganlu.mappers.ReplyMapper;
import com.vihu.ganlu.mappers.UserMapper;
import com.vihu.ganlu.service.AuditEventService;
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
import static org.mockito.Mockito.times;
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
    void unverifiedOrUnconsentedStudentCannotPublishContent() {
        UserEntity student = user(3, 2);
        student.setVerificationStatus("PENDING");
        student.setGuardianConsentStatus("PENDING");
        when(userMapper.findUserById(3)).thenReturn(student);
        when(messageMapper.selectMessageById(9)).thenReturn(message(9));

        assertThrows(SecurityException.class, () -> service.addMessage("需要审核", 3));
        assertThrows(SecurityException.class, () -> service.addReply(9, "需要审核", 3));

        verify(messageMapper, never()).insertMessage(any(MessageEntity.class));
        verify(replyMapper, never()).insertReply(any(ReplyEntity.class));
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
        assertEquals("用户#1", first.getDisplayName());
        assertEquals("用户#2", reply.getDisplayName());
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
        when(messageMapper.selectMessageForModeration(1)).thenReturn(message(1));

        assertThrows(SecurityException.class, () -> service.deleteMessage(1, 3));

        verify(messageMapper, never()).removeMessageForActor(any(), any(), any());
    }

    @Test
    void deletingOwnParentDoesNotMutateAnotherAuthorsReplies() {
        UserEntity author = user(2, 2);
        MessageEntity ownMessage = message(12);
        ownMessage.setUserId(2);
        when(userMapper.findUserById(2)).thenReturn(author);
        when(messageMapper.selectMessageForModeration(12)).thenReturn(ownMessage);
        when(messageMapper.removeMessageForActor(12, 2, "SELF_DELETE")).thenReturn(1);

        service.deleteMessage(12, 2);

        verify(messageMapper).removeMessageForActor(12, 2, "SELF_DELETE");
        verify(replyMapper, never()).selectRepliesForRemoval(any());
        verify(replyMapper, never()).removeReplyForActor(any(), any(), any());
    }

    @Test
    void teamAccountCannotDisposeAnotherUsersParent() {
        UserEntity team = user(2, 1);
        MessageEntity otherMessage = message(13);
        otherMessage.setUserId(9);
        when(userMapper.findUserById(2)).thenReturn(team);
        when(messageMapper.selectMessageForModeration(13)).thenReturn(otherMessage);

        assertThrows(SecurityException.class, () -> service.deleteMessage(13, 2));

        verify(messageMapper, never()).removeMessageForActor(any(), any(), any());
    }

    @Test
    void teamAccountCannotDisposeAnotherUsersReply() {
        UserEntity team = user(2, 1);
        ReplyEntity otherReply = reply(15, 13);
        otherReply.setUserId(9);
        when(userMapper.findUserById(2)).thenReturn(team);
        when(replyMapper.selectReplyForModeration(15)).thenReturn(otherReply);

        assertThrows(SecurityException.class, () -> service.deleteReply(15, 2));

        verify(replyMapper, never()).removeReplyForActor(any(), any(), any());
    }

    @Test
    void administratorCascadeRecordsReasonHistoryAndAuditForEveryReply() {
        ContentModerationHistoryMapper historyMapper = mock(ContentModerationHistoryMapper.class);
        AuditEventService auditService = mock(AuditEventService.class);
        service = new MessageServiceImpl(messageMapper, replyMapper, userMapper, historyMapper, auditService);

        UserEntity admin = user(1, 0);
        MessageEntity message = message(14);
        message.setUserId(9);
        ReplyEntity first = reply(41, 14);
        first.setUserId(7);
        ReplyEntity second = reply(42, 14);
        second.setUserId(8);
        when(messageMapper.selectMessageForModeration(14)).thenReturn(message);
        when(messageMapper.updateContentStatusByAdmin(14, 1, "REMOVED", "PARENT_POLICY", "parent note"))
                .thenReturn(1);
        when(replyMapper.selectRepliesForRemoval(14)).thenReturn(Arrays.asList(first, second));
        when(replyMapper.removeReplyForActor(any(), eq(1), eq("PARENT_POLICY"))).thenReturn(1);
        when(historyMapper.insert(any(ContentModerationHistoryEntity.class))).thenReturn(1);

        ContentReviewRequest request = new ContentReviewRequest();
        request.setContentType("MESSAGE");
        request.setContentId(14);
        request.setDecision("REMOVED");
        request.setReasonCode("PARENT_POLICY");
        request.setNote("parent note");

        service.reviewContent(request, admin);

        verify(replyMapper).removeReplyForActor(41, 1, "PARENT_POLICY");
        verify(replyMapper).removeReplyForActor(42, 1, "PARENT_POLICY");
        verify(historyMapper, times(3)).insert(any(ContentModerationHistoryEntity.class));
        verify(auditService).record(admin, "REPLY_REMOVE_CASCADE", "REPLY", 41,
                "SUCCESS", "PARENT_POLICY");
        verify(auditService).record(admin, "REPLY_REMOVE_CASCADE", "REPLY", 42,
                "SUCCESS", "PARENT_POLICY");
    }

    @Test
    void prefilterRejectsLinksAndHighRiskKeywordsBeforeInsert() {
        when(userMapper.findUserById(1)).thenReturn(user(1, 0));

        assertThrows(IllegalArgumentException.class,
                () -> service.addMessage("请访问 https://example.com", 1));
        assertThrows(IllegalArgumentException.class,
                () -> service.addMessage("请访问 example.com", 1));
        assertThrows(IllegalArgumentException.class,
                () -> service.addReply(7, "这里有赌博信息", 1));

        verify(messageMapper, never()).insertMessage(any(MessageEntity.class));
        verify(replyMapper, never()).insertReply(any(ReplyEntity.class));
    }

    @Test
    void prefilterRejectsShortWindowDuplicateWithoutSecondInsert() {
        when(userMapper.findUserById(1)).thenReturn(user(1, 0));
        when(messageMapper.insertMessage(any(MessageEntity.class))).thenReturn(1);

        service.addMessage("同一条留言", 1);

        assertThrows(IllegalArgumentException.class, () -> service.addMessage("同一条留言", 1));
        verify(messageMapper, times(1)).insertMessage(any(MessageEntity.class));
    }

    @Test
    void prefilterRejectsRecentPersistedDuplicateBeforeInsert() {
        when(userMapper.findUserById(1)).thenReturn(user(1, 0));
        when(messageMapper.countRecentDuplicate(eq(1), eq("已存储的重复内容"), any(java.util.Date.class)))
                .thenReturn(1);

        assertThrows(IllegalArgumentException.class,
                () -> service.addMessage("已存储的重复内容", 1));

        verify(messageMapper, never()).insertMessage(any(MessageEntity.class));
    }

    @Test
    void administratorCanDeleteAnyContentAndUsersCanDeleteTheirOwn() {
        when(userMapper.findUserById(1)).thenReturn(user(1, 0));
        when(userMapper.findUserById(2)).thenReturn(user(2, 1));
        MessageEntity otherMessage = message(4);
        otherMessage.setUserId(9);
        MessageEntity teamMessage = message(4);
        teamMessage.setUserId(2);
        when(messageMapper.selectMessageForModeration(4)).thenReturn(otherMessage, teamMessage);
        ReplyEntity otherReply = reply(5, 1);
        otherReply.setUserId(9);
        ReplyEntity teamReply = reply(5, 1);
        teamReply.setUserId(2);
        when(replyMapper.selectReplyForModeration(5)).thenReturn(otherReply, teamReply);
        when(messageMapper.removeMessageForActor(any(), any(), any())).thenReturn(1);
        when(replyMapper.removeReplyForActor(any(), any(), any())).thenReturn(1);

        service.deleteMessage(4, 1, "MODERATION_REMOVE");
        service.deleteMessage(4, 2);
        service.deleteReply(5, 1, "MODERATION_REMOVE");
        service.deleteReply(5, 2);

        verify(messageMapper).removeMessageForActor(4, 1, "MODERATION_REMOVE");
        verify(messageMapper).removeMessageForActor(4, 2, "SELF_DELETE");
        verify(replyMapper).removeReplyForActor(5, 1, "MODERATION_REMOVE");
        verify(replyMapper).removeReplyForActor(5, 2, "SELF_DELETE");
    }

    private UserEntity user(int id, int level) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setLevel(level);
        if (level == 2) {
            user.setVerificationStatus("VERIFIED");
            user.setGuardianConsentStatus("CONSENTED");
        }
        return user;
    }

    private MessageEntity message(int id) {
        MessageEntity message = new MessageEntity();
        message.setId(id);
        message.setUserId(1);
        message.setStatus(true);
        message.setContentStatus("APPROVED");
        return message;
    }

    private ReplyEntity reply(int id, int messageId) {
        ReplyEntity reply = new ReplyEntity();
        reply.setId(id);
        reply.setMessageId(messageId);
        reply.setUserId(2);
        reply.setStatus(true);
        reply.setContentStatus("APPROVED");
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

