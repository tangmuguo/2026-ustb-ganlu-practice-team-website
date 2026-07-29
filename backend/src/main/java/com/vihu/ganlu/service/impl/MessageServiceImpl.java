package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.MessageEntity;
import com.vihu.ganlu.entitys.ReplyEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.mappers.MessageMapper;
import com.vihu.ganlu.mappers.ReplyMapper;
import com.vihu.ganlu.mappers.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MessageServiceImpl {
    @Autowired
    private MessageMapper messageMapper;
    @Autowired
    private ReplyMapper replyMapper;
    @Resource
    private UserMapper userMapper;

    // 添加留言
    public int addMessage(MessageEntity message, Integer userId) {
        UserEntity user = userMapper.findUserById(userId);
        if (!canPublish(user)) {
            return 0;
        }
        message.setUserId(userId);
        message.setStatus(true);
        int i = messageMapper.insertMessage(message);
        return i;
    }

    // 获取留言列表（分页）
    public Map<String, Object> getMessages(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<MessageEntity> messages = messageMapper.selectMessages(offset, pageSize);
        // 获取每条留言的回复
        for (MessageEntity message : messages) {
            List<ReplyEntity> replies = replyMapper.selectRepliesByMessageId(message.getId());
            message.setReplies(replies);
            // 设置用户信息
            UserEntity user = userMapper.findUserById(message.getUserId());
            if (user != null) {
                message.setUsername(user.getUsername());
                message.setTeamname(user.getTeamname());
            }
            // 设置回复的用户信息
            if (replies != null) {
                for (ReplyEntity reply : replies) {
                    UserEntity replyUser = userMapper.findUserById(reply.getUserId());
                    if (replyUser != null) {
                        reply.setUsername(replyUser.getUsername());
                        reply.setTeamname(replyUser.getTeamname());
                    }
                }
            }
        }
        int total = messageMapper.countMessages();
        Map<String, Object> data = new HashMap<>();
        data.put("messages", messages);
        data.put("total", total);
        return data;
    }

    // 删除留言（管理员）
    public int deleteMessage(Integer messageId, Integer userId) {
        UserEntity user = userMapper.findUserById(userId);
        if (!canDelete(user)) {
            return 0;
        }
        int  i = messageMapper.deleteMessage(messageId);
        return i;
    }

    // 添加回复
    public int addReply(ReplyEntity reply, Integer userId) {
        UserEntity user = userMapper.findUserById(userId);
        if (!canPublish(user)) {
            return 0;
        }
        MessageEntity message = messageMapper.selectMessageById(reply.getMessageId());
        if (message == null || !message.getStatus()) {
            return 0;
        }
        reply.setUserId(userId);
        reply.setStatus(true);
        int i = replyMapper.insertReply(reply);
        return i;
    }

    // 删除回复（管理员）
    public int deleteReply(Integer replyId, Integer userId) {
        UserEntity user = userMapper.findUserById(userId);
        if (!canDelete(user)) {
            return 0;
        }
        return replyMapper.deleteReply(replyId);
    }

    private boolean canPublish(UserEntity user) {
        return user != null && user.getLevel() != null
                && user.getLevel() >= 0 && user.getLevel() <= 2;
    }

    private boolean canDelete(UserEntity user) {
        return user != null && user.getLevel() != null
                && (user.getLevel() == 0 || user.getLevel() == 1);
    }

}
