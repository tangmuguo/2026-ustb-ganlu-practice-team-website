package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.MessageEntity;
import com.vihu.ganlu.entitys.ReplyEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.message.MessageCreateRequest;
import com.vihu.ganlu.entitys.message.ReplyCreateRequest;
import com.vihu.ganlu.mappers.MessageMapper;
import com.vihu.ganlu.mappers.ReplyMapper;
import com.vihu.ganlu.mappers.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class MessageServiceImpl {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MAX_REPLY_LENGTH = 300;

    @Autowired
    private MessageMapper messageMapper;
    @Autowired
    private ReplyMapper replyMapper;
    @Resource
    private UserMapper userMapper;

    // 添加留言
    public MessageEntity addMessage(MessageCreateRequest request, Integer userId) {
        UserEntity user = userMapper.findUserById(userId);
        if (!canPublish(user)) {
            throw new IllegalArgumentException("当前用户不能留言");
        }
        String content = normalizeContent(request == null ? null : request.getContent(), MAX_MESSAGE_LENGTH, "留言");
        MessageEntity message = new MessageEntity();
        message.setUserId(userId);
        message.setContent(content);
        message.setStatus(true);
        messageMapper.insertMessage(message);
        return message;
    }

    // 获取留言列表（分页）
    public Map<String, Object> getMessages(int page, int pageSize) {
        validatePage(page, pageSize);
        int offset = (page - 1) * pageSize;
        List<MessageEntity> messages = messageMapper.selectMessages(offset, pageSize);

        if (!messages.isEmpty()) {
            List<Integer> messageIds = messages.stream()
                    .map(MessageEntity::getId)
                    .collect(Collectors.toList());
            List<ReplyEntity> replies = replyMapper.selectRepliesByMessageIds(messageIds);
            Map<Integer, List<ReplyEntity>> repliesByMessageId = replies.stream()
                    .collect(Collectors.groupingBy(ReplyEntity::getMessageId));
            for (MessageEntity message : messages) {
                List<ReplyEntity> messageReplies = repliesByMessageId.get(message.getId());
                message.setReplies(messageReplies == null ? Collections.emptyList() : messageReplies);
            }
        }

        int total = messageMapper.countMessages();
        Map<String, Object> data = new HashMap<>();
        data.put("messages", messages);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }

    // 删除留言（管理员）
    public void deleteMessage(Integer messageId, Integer userId) {
        validateId(messageId, "留言ID");
        UserEntity user = userMapper.findUserById(userId);
        if (!canDelete(user)) {
            throw forbidden("无删除权限");
        }
        if (messageMapper.selectMessageById(messageId) == null) {
            throw new NoSuchElementException("留言不存在或已删除");
        }
        messageMapper.deleteMessage(messageId);
    }

    // 添加回复
    public ReplyEntity addReply(ReplyCreateRequest request, Integer userId) {
        UserEntity user = userMapper.findUserById(userId);
        if (!canPublish(user)) {
            throw new IllegalArgumentException("当前用户不能回复");
        }
        validateId(request == null ? null : request.getMessageId(), "留言ID");
        MessageEntity message = messageMapper.selectMessageById(request.getMessageId());
        if (message == null || !message.getStatus()) {
            throw new NoSuchElementException("留言不存在或已删除");
        }
        String content = normalizeContent(request.getContent(), MAX_REPLY_LENGTH, "回复");
        ReplyEntity reply = new ReplyEntity();
        reply.setMessageId(request.getMessageId());
        reply.setUserId(userId);
        reply.setContent(content);
        reply.setStatus(true);
        replyMapper.insertReply(reply);
        return reply;
    }

    // 删除回复（管理员）
    public void deleteReply(Integer replyId, Integer userId) {
        validateId(replyId, "回复ID");
        UserEntity user = userMapper.findUserById(userId);
        if (!canDelete(user)) {
            throw forbidden("无删除权限");
        }
        if (replyMapper.selectReplyById(replyId) == null) {
            throw new NoSuchElementException("回复不存在或已删除");
        }
        replyMapper.deleteReply(replyId);
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

    private void validateId(Integer id, String name) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException(name + "不合法");
        }
    }

    private String normalizeContent(String content, int maxLength, String fieldName) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "内容不能为空");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "内容不能超过" + maxLength + "字");
        }
        return normalized;
    }

    private RuntimeException forbidden(String message) {
        return new SecurityException(message);
    }
}

