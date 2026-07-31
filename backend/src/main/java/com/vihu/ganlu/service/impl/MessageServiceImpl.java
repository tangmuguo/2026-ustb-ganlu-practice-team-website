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

    private static final Integer STATUS_NORMAL = 1;
    private static final Integer STATUS_DELETED = 0;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private ReplyMapper replyMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public Map<String, Object> getMessages(int page, int pageSize) {
        // 分页参数边界校验
        if (page < 1) {
            throw new RuntimeException("page参数必须≥1");
        }
        if (pageSize < 1 || pageSize > 50) {
            throw new RuntimeException("pageSize范围1~50");
        }

        int offset = (page - 1) * pageSize;

        // 1. 分页查询有效留言
        List<MessageEntity> pageMessageList = messageMapper.selectPage(STATUS_NORMAL, offset, pageSize);
        int total = messageMapper.countByStatus(STATUS_NORMAL);

        // 空集合兜底
        if (pageMessageList == null) {
            pageMessageList = Collections.emptyList();
        }

        // 构造返回结果
        Map<String, Object> result = new HashMap<>(4);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);

        if (pageMessageList.isEmpty()) {
            result.put("messages", Collections.emptyList());
            return result;
        }

        // 2. 收集留言ID，批量查询回复（消除N+1）
        List<Integer> messageIdList = pageMessageList.stream()
                .map(MessageEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<ReplyEntity> allReplyList = Collections.emptyList();
        if (!messageIdList.isEmpty()) {
            List<ReplyEntity> tempList = replyMapper.selectByMessageIdList(messageIdList, STATUS_NORMAL);
            if (tempList != null) {
                allReplyList = tempList;
            }
        }

        // 3. 收集所有用户ID（留言作者+回复作者）
        Set<Integer> userIdSet = new HashSet<>();
        for (MessageEntity msg : pageMessageList) {
            if (msg.getUserId() != null) {
                userIdSet.add(msg.getUserId());
            }
        }
        for (ReplyEntity reply : allReplyList) {
            if (reply.getUserId() != null) {
                userIdSet.add(reply.getUserId());
            }
        }

        // 4. 批量查询用户信息，转Map
        Map<Integer, UserEntity> userMap = new HashMap<>();
        if (!userIdSet.isEmpty()) {
            List<Integer> userIdList = new ArrayList<>(userIdSet);
            List<UserEntity> userList = userMapper.selectUserByIdList(userIdList);
            if (userList != null && !userList.isEmpty()) {
                userMap = userList.stream()
                        .filter(u -> u.getId() != null)
                        .collect(Collectors.toMap(
                                UserEntity::getId,
                                u -> u,
                                (exist, newVal) -> exist
                        ));
            }
        }

        // 5. 回复按留言ID分组
        Map<Integer, List<ReplyEntity>> replyGroupMap = allReplyList.stream()
                .collect(Collectors.groupingBy(ReplyEntity::getMessageId));

        // 6. 组装数据：填充回复列表、用户名、团队名
        for (MessageEntity msg : pageMessageList) {
            List<ReplyEntity> replyList = replyGroupMap.get(msg.getId());
            if (replyList == null) {
                replyList = Collections.emptyList();
            }
            msg.setReplies(replyList);

            // 填充留言作者信息
            UserEntity msgUser = userMap.get(msg.getUserId());
            if (msgUser != null) {
                msg.setUsername(msgUser.getUsername());
                msg.setTeamname(msgUser.getTeamname());
            }

            // 填充回复作者信息
            for (ReplyEntity reply : replyList) {
                UserEntity replyUser = userMap.get(reply.getUserId());
                if (replyUser != null) {
                    reply.setUsername(replyUser.getUsername());
                    reply.setTeamname(replyUser.getTeamname());
                }
            }
        }

        result.put("messages", pageMessageList);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addMessage(String content, Integer loginUserId) {
        // 参数校验
        if (loginUserId == null) {
            return 0;
        }
        if (content == null || content.trim().isEmpty()) {
            return 0;
        }
        String trimContent = content.trim();
        if (trimContent.length() > 500) {
            return 0;
        }

        MessageEntity entity = new MessageEntity();
        entity.setUserId(loginUserId);
        entity.setContent(trimContent);
        entity.setStatus(STATUS_NORMAL);
        entity.setCreateTime(new Date());
        messageMapper.insert(entity);
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addReply(Integer messageId, String content, Integer loginUserId) {
        // 参数校验
        if (messageId == null || loginUserId == null) {
            return 0;
        }
        if (content == null || content.trim().isEmpty()) {
            return 0;
        }
        String trimContent = content.trim();
        if (trimContent.length() > 300) {
            return 0;
        }

        // 校验留言存在且未删除
        MessageEntity message = messageMapper.selectById(messageId);
        if (message == null) {
            return 0;
        }
        if (!STATUS_NORMAL.equals(message.getStatus())) {
            return 0;
        }

        ReplyEntity reply = new ReplyEntity();
        reply.setMessageId(messageId);
        reply.setUserId(loginUserId);
        reply.setContent(trimContent);
        reply.setStatus(STATUS_NORMAL);
        reply.setCreateTime(new Date());
        replyMapper.insert(reply);
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteMessage(Integer messageId, UserEntity loginUser) {
        // 参数与权限校验：仅 level 0、1 可删除
        if (messageId == null || loginUser == null) {
            return 0;
        }
        Integer level = loginUser.getLevel();
        if (level == null) {
            return 0;
        }
        if (!Integer.valueOf(0).equals(level) && !Integer.valueOf(1).equals(level)) {
            return 0;
        }

        // 校验留言存在且未删除
        MessageEntity message = messageMapper.selectById(messageId);
        if (message == null) {
            return 0;
        }
        if (!STATUS_NORMAL.equals(message.getStatus())) {
            return 0;
        }

        // 逻辑删除
        messageMapper.logicDeleteById(messageId);
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteReply(Integer replyId, UserEntity loginUser) {
        // 参数与权限校验：仅 level 0、1 可删除
        if (replyId == null || loginUser == null) {
            return 0;
        }
        Integer level = loginUser.getLevel();
        if (level == null) {
            return 0;
        }
        if (!Integer.valueOf(0).equals(level) && !Integer.valueOf(1).equals(level)) {
            return 0;
        }

        // 校验回复存在且未删除
        ReplyEntity reply = replyMapper.selectById(replyId);
        if (reply == null) {
            return 0;
        }
        if (!STATUS_NORMAL.equals(reply.getStatus())) {
            return 0;
        }

        // 逻辑删除
        replyMapper.logicDeleteById(replyId);
        return 1;
    }
}