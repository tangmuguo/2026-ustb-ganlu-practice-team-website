package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.MessageEntity;
import com.vihu.ganlu.entitys.ReplyEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.mappers.MessageMapper;
import com.vihu.ganlu.mappers.ReplyMapper;
import com.vihu.ganlu.mappers.UserMapper;
import com.vihu.ganlu.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MessageServiceImpl implements MessageService {

    private static final int STATUS_NORMAL = 1;
    private static final int STATUS_DELETED = 0;

    @Autowired
    private MessageMapper messageMapper;
    @Autowired
    private ReplyMapper replyMapper;
    @Autowired
    private UserMapper userMapper;

    @Override
    public Map<String, Object> getMessageList(int page, int pageSize) {
        // 分页参数校验
        if (page < 1) {
            throw new RuntimeException("page参数必须≥1");
        }
        if (pageSize < 1 || pageSize > 50) {
            throw new RuntimeException("pageSize范围1~50");
        }
        int offset = (page - 1) * pageSize;

        // 分页查询有效留言
        List<MessageEntity> pageMessageList = messageMapper.selectPage(STATUS_NORMAL, offset, pageSize);
        int total = messageMapper.countByStatus(STATUS_NORMAL);

        if (pageMessageList.isEmpty()) {
            return Map.of(
                    "messages", Collections.emptyList(),
                    "total", total,
                    "page", page,
                    "pageSize", pageSize
            );
        }

        // 收集messageId，批量查询回复，消除N+1
        List<Integer> messageIdList = pageMessageList.stream()
                .map(MessageEntity::getId)
                .collect(Collectors.toList());
        List<ReplyEntity> allReplyList = replyMapper.selectByMessageIdList(messageIdList, STATUS_NORMAL);

        // 收集全部用户ID
        Set<Integer> userIdSet = new HashSet<>();
        pageMessageList.forEach(m -> userIdSet.add(m.getUserId()));
        allReplyList.forEach(r -> userIdSet.add(r.getUserId()));
        List<Integer> userIdList = new ArrayList<>(userIdSet);

        // 批量查询用户信息
        Map<Integer, UserEntity> userMap = userMapper.selectByIdList(userIdList).stream()
                .collect(Collectors.toMap(UserEntity::getId, u -> u, (exist, newVal) -> exist));

        // 回复分组
        Map<Integer, List<ReplyEntity>> replyGroupMap = allReplyList.stream()
                .collect(Collectors.groupingBy(ReplyEntity::getMessageId));

        // 填充回复、用户名
        for (MessageEntity msg : pageMessageList) {
            msg.setReplies(replyGroupMap.getOrDefault(msg.getId(), Collections.emptyList()));
            UserEntity msgUser = userMap.get(msg.getUserId());
            if (msgUser != null) {
                msg.setUsername(msgUser.getUsername());
                msg.setTeamname(msgUser.getTeamname());
            }
            for (ReplyEntity reply : msg.getReplies()) {
                UserEntity replyUser = userMap.get(reply.getUserId());
                if (replyUser != null) {
                    reply.setUsername(replyUser.getUsername());
                    reply.setTeamname(replyUser.getTeamname());
                }
            }
        }

        return Map.of(
                "messages", pageMessageList,
                "total", total,
                "page", page,
                "pageSize", pageSize
        );
    }

    @Override
    @Transactional
    public void addMessage(String content, Integer loginUserId) {
        MessageEntity entity = new MessageEntity();
        entity.setUserId(loginUserId);
        entity.setContent(content);
        entity.setStatus(STATUS_NORMAL);
        entity.setCreateTime(new Date());
        messageMapper.insert(entity);
    }

    @Override
    @Transactional
    public void addReply(Integer messageId, String content, Integer loginUserId) {
        // 校验留言存在且未删除
        MessageEntity message = messageMapper.selectById(messageId);
        if (message == null || !STATUS_NORMAL.equals(message.getStatus())) {
            throw new RuntimeException("留言不存在或已删除");
        }
        ReplyEntity reply = new ReplyEntity();
        reply.setMessageId(messageId);
        reply.setUserId(loginUserId);
        reply.setContent(content);
        reply.setStatus(STATUS_NORMAL);
        reply.setCreateTime(new Date());
        replyMapper.insert(reply);
    }

    @Override
    @Transactional
    public void deleteMessage(Integer messageId, UserEntity loginUser) {
        // 权限：仅level 0/1允许删除
        Integer level = loginUser.getLevel();
        if (!Integer.valueOf(0).equals(level) && !Integer.valueOf(1).equals(level)) {
            throw new RuntimeException("403，权限不足，无法删除");
        }
        MessageEntity message = messageMapper.selectById(messageId);
        if (message == null || !STATUS_NORMAL.equals(message.getStatus())) {
            throw new RuntimeException("留言不存在或已删除");
        }
        // 逻辑删除留言（回复查询依靠status过滤，不更新回复）
        messageMapper.logicDeleteById(messageId);
    }

    @Override
    @Transactional
    public void deleteReply(Integer replyId, UserEntity loginUser) {
        Integer level = loginUser.getLevel();
        if (!Integer.valueOf(0).equals(level) && !Integer.valueOf(1).equals(level)) {
            throw new RuntimeException("403，权限不足，无法删除");
        }
        ReplyEntity reply = replyMapper.selectById(replyId);
        if (reply == null || !STATUS_NORMAL.equals(reply.getStatus())) {
            throw new RuntimeException("回复不存在或已删除");
        }
        replyMapper.logicDeleteById(replyId);
    }
}