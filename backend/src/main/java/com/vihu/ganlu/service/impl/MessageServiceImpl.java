package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.MessageEntity;
import com.vihu.ganlu.entitys.ReplyEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.mappers.MessageMapper;
import com.vihu.ganlu.mappers.ReplyMapper;
import com.vihu.ganlu.mappers.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MessageServiceImpl {
    private final MessageMapper messageMapper;
    private final ReplyMapper replyMapper;
    private final UserMapper userMapper;

    public MessageServiceImpl(MessageMapper messageMapper, ReplyMapper replyMapper, UserMapper userMapper) {
        this.messageMapper = messageMapper;
        this.replyMapper = replyMapper;
        this.userMapper = userMapper;
    }

    public MessageEntity addMessage(String content, Integer userId) {
        requireContent(content, 1, 500, "留言");
        UserEntity user = userMapper.findUserById(userId);
        if (!canPublish(user)) throw new IllegalArgumentException("当前账号不能发布留言");
        MessageEntity message = new MessageEntity();
        message.setUserId(userId);
        message.setContent(content.trim());
        message.setStatus(true);
        messageMapper.insertMessage(message);
        return message;
    }

    public Map<String, Object> getMessages(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 50) throw new IllegalArgumentException("分页参数不正确");
        int offset = (page - 1) * pageSize;
        List<MessageEntity> messages = messageMapper.selectMessages(offset, pageSize);
        List<Integer> messageIds = new ArrayList<>();
        for (MessageEntity message : messages) messageIds.add(message.getId());

        Map<Integer, List<ReplyEntity>> repliesByMessage = new HashMap<>();
        if (!messageIds.isEmpty()) {
            for (ReplyEntity reply : replyMapper.selectRepliesByMessageIds(messageIds)) {
                repliesByMessage.computeIfAbsent(reply.getMessageId(), ignored -> new ArrayList<>()).add(reply);
            }
        }
        for (MessageEntity message : messages) {
            message.setReplies(repliesByMessage.getOrDefault(message.getId(), new ArrayList<>()));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("messages", messages);
        data.put("total", messageMapper.countMessages());
        data.put("page", page);
        data.put("pageSize", pageSize);
        return data;
    }

    @Transactional
    public boolean deleteMessage(Integer messageId, Integer userId) {
        requireDeletePermission(userId);
        if (messageId == null) throw new IllegalArgumentException("留言编号不能为空");
        replyMapper.deleteRepliesByMessageId(messageId);
        return messageMapper.deleteMessage(messageId) > 0;
    }

    public ReplyEntity addReply(Integer messageId, String content, Integer userId) {
        requireContent(content, 1, 300, "回复");
        UserEntity user = userMapper.findUserById(userId);
        if (!canPublish(user)) throw new IllegalArgumentException("当前账号不能发布回复");
        MessageEntity message = messageMapper.selectMessageById(messageId);
        if (message == null || !Boolean.TRUE.equals(message.getStatus())) {
            throw new IllegalArgumentException("留言不存在或已删除");
        }
        ReplyEntity reply = new ReplyEntity();
        reply.setMessageId(messageId);
        reply.setUserId(userId);
        reply.setContent(content.trim());
        reply.setStatus(true);
        replyMapper.insertReply(reply);
        return reply;
    }

    public boolean deleteReply(Integer replyId, Integer userId) {
        requireDeletePermission(userId);
        if (replyId == null) throw new IllegalArgumentException("回复编号不能为空");
        return replyMapper.deleteReply(replyId) > 0;
    }

    private void requireDeletePermission(Integer userId) {
        UserEntity user = userMapper.findUserById(userId);
        if (!canDelete(user)) throw new IllegalArgumentException("当前账号不能删除留言或回复");
    }

    private void requireContent(String value, int min, int max, String label) {
        if (value == null) throw new IllegalArgumentException(label + "内容不能为空");
        String trimmed = value.trim();
        int length = trimmed.codePointCount(0, trimmed.length());
        if (length < min || length > max) {
            throw new IllegalArgumentException(label + "内容长度应为" + min + "到" + max + "个字符");
        }
    }

    private boolean canPublish(UserEntity user) {
        return user != null && user.getLevel() != null && user.getLevel() >= 0 && user.getLevel() <= 2;
    }

    private boolean canDelete(UserEntity user) {
        return user != null && user.getLevel() != null && (user.getLevel() == 0 || user.getLevel() == 1);
    }
}
