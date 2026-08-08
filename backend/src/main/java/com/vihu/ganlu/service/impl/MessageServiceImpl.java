package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.MessageEntity;
import com.vihu.ganlu.entitys.ReplyEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.message.MessageCreateRequest;
import com.vihu.ganlu.entitys.message.ReplyCreateRequest;
import com.vihu.ganlu.mappers.MessageMapper;
import com.vihu.ganlu.mappers.ReplyMapper;
import com.vihu.ganlu.mappers.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class MessageServiceImpl {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_OFFSET = 10000;
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MAX_REPLY_LENGTH = 300;

    private final MessageMapper messageMapper;
    private final ReplyMapper replyMapper;
    private final UserMapper userMapper;

    public MessageServiceImpl(MessageMapper messageMapper, ReplyMapper replyMapper, UserMapper userMapper) {
        this.messageMapper = messageMapper;
        this.replyMapper = replyMapper;
        this.userMapper = userMapper;
    }

    public MessageEntity addMessage(MessageCreateRequest request, Integer userId) {
        return addMessage(request == null ? null : request.getContent(), userId);
    }

    public MessageEntity addMessage(String content, Integer userId) {
        String normalized = normalizeContent(content, MAX_MESSAGE_LENGTH, "留言");
        UserEntity user = userMapper.findUserById(userId);
        if (!canPublish(user)) {
            throw new IllegalArgumentException("当前用户不能留言");
        }
        MessageEntity message = new MessageEntity();
        message.setUserId(userId);
        message.setContent(normalized);
        message.setStatus(true);
        messageMapper.insertMessage(message);
        return message;
    }

    public Map<String, Object> getMessages(int page, int pageSize) {
        validatePage(page, pageSize);
        int offset = calculateOffset(page, pageSize);
        List<MessageEntity> messages = messageMapper.selectMessages(offset, pageSize);

        if (!messages.isEmpty()) {
            List<Integer> messageIds = messages.stream()
                    .map(MessageEntity::getId)
                    .collect(Collectors.toList());
            List<ReplyEntity> replies = replyMapper.selectRepliesByMessageIds(messageIds);
            Map<Integer, List<ReplyEntity>> repliesByMessageId = replies.stream()
                    .collect(Collectors.groupingBy(ReplyEntity::getMessageId));
            for (MessageEntity message : messages) {
                fillUserFallback(message);
                List<ReplyEntity> messageReplies = repliesByMessageId.get(message.getId());
                if (messageReplies == null) {
                    message.setReplies(Collections.emptyList());
                } else {
                    messageReplies.forEach(this::fillReplyUserFallback);
                    message.setReplies(messageReplies);
                }
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("messages", messages);
        data.put("total", messageMapper.countMessages());
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }

    @Transactional
    public void deleteMessage(Integer messageId, Integer userId) {
        validateId(messageId, "留言ID");
        requireDeletePermission(userId);
        if (messageMapper.selectMessageById(messageId) == null) {
            throw new NoSuchElementException("留言不存在或已删除");
        }
        replyMapper.deleteRepliesByMessageId(messageId);
        messageMapper.deleteMessage(messageId);
    }

    public ReplyEntity addReply(ReplyCreateRequest request, Integer userId) {
        return addReply(
                request == null ? null : request.getMessageId(),
                request == null ? null : request.getContent(),
                userId
        );
    }

    public ReplyEntity addReply(Integer messageId, String content, Integer userId) {
        validateId(messageId, "留言ID");
        String normalized = normalizeContent(content, MAX_REPLY_LENGTH, "回复");
        UserEntity user = userMapper.findUserById(userId);
        if (!canPublish(user)) {
            throw new IllegalArgumentException("当前用户不能回复");
        }
        MessageEntity message = messageMapper.selectMessageById(messageId);
        if (message == null || !Boolean.TRUE.equals(message.getStatus())) {
            throw new NoSuchElementException("留言不存在或已删除");
        }
        ReplyEntity reply = new ReplyEntity();
        reply.setMessageId(messageId);
        reply.setUserId(userId);
        reply.setContent(normalized);
        reply.setStatus(true);
        replyMapper.insertReply(reply);
        return reply;
    }

    public void deleteReply(Integer replyId, Integer userId) {
        validateId(replyId, "回复ID");
        requireDeletePermission(userId);
        if (replyMapper.selectReplyById(replyId) == null) {
            throw new NoSuchElementException("回复不存在或已删除");
        }
        replyMapper.deleteReply(replyId);
    }

    private void requireDeletePermission(Integer userId) {
        UserEntity user = userMapper.findUserById(userId);
        if (!canDelete(user)) {
            throw new SecurityException("无删除权限");
        }
    }

    private boolean canPublish(UserEntity user) {
        return user != null && user.getLevel() != null
                && user.getLevel() >= 0 && user.getLevel() <= 2;
    }

    private boolean canDelete(UserEntity user) {
        return user != null && user.getLevel() != null
                && (user.getLevel() == 0 || user.getLevel() == 1);
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1) {
            throw new IllegalArgumentException("page必须大于等于1");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize必须在1到50之间");
        }
    }

    private int calculateOffset(int page, int pageSize) {
        long offset = ((long) page - 1L) * (long) pageSize;
        if (offset > MAX_OFFSET) {
            throw new IllegalArgumentException("page过大");
        }
        return (int) offset;
    }

    private void validateId(Integer id, String name) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException(name + "不合法");
        }
    }

    private String normalizeContent(String content, int maxLength, String fieldName) {
        String normalized = content == null ? "" : content.trim();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1) {
            throw new IllegalArgumentException(fieldName + "内容不能为空");
        }
        if (length > maxLength) {
            throw new IllegalArgumentException(fieldName + "内容不能超过" + maxLength + "字");
        }
        return normalized;
    }

    private void fillUserFallback(MessageEntity message) {
        if (message.getUsername() == null || message.getUsername().trim().isEmpty()) {
            message.setUsername("用户#" + message.getUserId());
        }
    }

    private void fillReplyUserFallback(ReplyEntity reply) {
        if (reply.getUsername() == null || reply.getUsername().trim().isEmpty()) {
            reply.setUsername("用户#" + reply.getUserId());
        }
    }
}
