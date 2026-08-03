package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.MessageEntity;
import com.vihu.ganlu.entitys.ReplyEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.exception.BadRequestException;
import com.vihu.ganlu.exception.ForbiddenException;
import com.vihu.ganlu.exception.NotFoundException;
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
        if (page < 1) {
            throw new BadRequestException("page 参数必须≥1");
        }
        if (pageSize < 1 || pageSize > 50) {
            throw new BadRequestException("pageSize 范围1~50");
        }

        int offset = (page - 1) * pageSize;
        List<MessageEntity> pageMessageList = messageMapper.selectPage(STATUS_NORMAL, offset, pageSize);
        int total = messageMapper.countByStatus(STATUS_NORMAL);

        if (pageMessageList == null) {
            pageMessageList = Collections.emptyList();
        }

        Map<String, Object> result = new HashMap<>(4);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);

        if (pageMessageList.isEmpty()) {
            result.put("messages", Collections.emptyList());
            return result;
        }

        List<Integer> messageIdList = pageMessageList.stream()
                .map(MessageEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<ReplyEntity> allReplyList = Collections.emptyList();
        if (!messageIdList.isEmpty()) {
            List<ReplyEntity> temp = replyMapper.selectByMessageIdList(messageIdList, STATUS_NORMAL);
            if (temp != null) {
                allReplyList = temp;
            }
        }

        Set<Integer> userIdSet = new HashSet<>();
        for (MessageEntity msg : pageMessageList) {
            if (msg.getUserId() != null) userIdSet.add(msg.getUserId());
        }
        for (ReplyEntity reply : allReplyList) {
            if (reply.getUserId() != null) userIdSet.add(reply.getUserId());
        }

        Map<Integer, UserEntity> userMap = new HashMap<>();
        if (!userIdSet.isEmpty()) {
            List<UserEntity> userList = userMapper.selectUserByIdList(new ArrayList<>(userIdSet));
            if (userList != null && !userList.isEmpty()) {
                userMap = userList.stream()
                        .filter(u -> u.getId() != null)
                        .collect(Collectors.toMap(UserEntity::getId, u -> u, (e, n) -> e));
            }
        }

        Map<Integer, List<ReplyEntity>> replyGroupMap = allReplyList.stream()
                .collect(Collectors.groupingBy(ReplyEntity::getMessageId));

        for (MessageEntity msg : pageMessageList) {
            List<ReplyEntity> replyList = replyGroupMap.getOrDefault(msg.getId(), Collections.emptyList());
            msg.setReplies(replyList);

            // 【修改】用户名统一设置：用户存在用真实名称，不存在用ID占位，保证前端不显示空白
            msg.setUsername(getDisplayUsername(msg.getUserId(), userMap));
            UserEntity msgUser = userMap.get(msg.getUserId());
            if (msgUser != null) {
                msg.setTeamname(msgUser.getTeamname());
            }

            for (ReplyEntity reply : replyList) {
                // 【修改】回复用户名同样增加占位逻辑
                reply.setUsername(getDisplayUsername(reply.getUserId(), userMap));
                UserEntity replyUser = userMap.get(reply.getUserId());
                if (replyUser != null) {
                    reply.setTeamname(replyUser.getTeamname());
                }
            }
        }

        result.put("messages", pageMessageList);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer addMessage(String content, Integer loginUserId) {
        if (loginUserId == null) {
            throw new BadRequestException("用户未登录");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new BadRequestException("留言内容不能为空");
        }
        String trimContent = content.trim();
        if (trimContent.length() > 500) {
            throw new BadRequestException("留言长度不能超过500字符");
        }

        MessageEntity entity = new MessageEntity();
        entity.setUserId(loginUserId);
        entity.setContent(trimContent);
        entity.setStatus(STATUS_NORMAL);
        entity.setCreateTime(new Date());
        messageMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addReply(Integer messageId, String content, Integer loginUserId) {
        if (messageId == null || loginUserId == null) {
            throw new BadRequestException("参数非法");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new BadRequestException("回复内容不能为空");
        }
        String trimContent = content.trim();
        if (trimContent.length() > 300) {
            throw new BadRequestException("回复长度不能超过300字符");
        }

        MessageEntity message = messageMapper.selectById(messageId);
        if (message == null || !STATUS_NORMAL.equals(message.getStatus())) {
            throw new NotFoundException("留言不存在或已删除");
        }

        ReplyEntity reply = new ReplyEntity();
        reply.setMessageId(messageId);
        reply.setUserId(loginUserId);
        reply.setContent(trimContent);
        reply.setStatus(STATUS_NORMAL);
        reply.setCreateTime(new Date());
        replyMapper.insert(reply);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMessage(Integer messageId, UserEntity loginUser) {
        if (messageId == null || loginUser == null) {
            throw new BadRequestException("参数非法");
        }
        Integer level = loginUser.getLevel();
        if (level == null || (!Integer.valueOf(0).equals(level) && !Integer.valueOf(1).equals(level))) {
            throw new ForbiddenException("权限不足，无法删除");
        }

        MessageEntity message = messageMapper.selectById(messageId);
        if (message == null || !STATUS_NORMAL.equals(message.getStatus())) {
            throw new NotFoundException("留言不存在或已删除");
        }

        messageMapper.logicDeleteById(messageId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReply(Integer replyId, UserEntity loginUser) {
        if (replyId == null || loginUser == null) {
            throw new BadRequestException("参数非法");
        }
        Integer level = loginUser.getLevel();
        if (level == null || (!Integer.valueOf(0).equals(level) && !Integer.valueOf(1).equals(level))) {
            throw new ForbiddenException("权限不足，无法删除");
        }

        ReplyEntity reply = replyMapper.selectById(replyId);
        if (reply == null || !STATUS_NORMAL.equals(reply.getStatus())) {
            throw new NotFoundException("回复不存在或已删除");
        }

        replyMapper.logicDeleteById(replyId);
    }

    /**
     * 【新增】获取展示用的用户名
     * 用户存在且用户名有效时返回真实用户名，不存在时返回「用户#ID」占位
     * 保证前端始终有可展示的文本，避免用户删除后显示空白
     */
    private String getDisplayUsername(Integer userId, Map<Integer, UserEntity> userMap) {
        if (userId == null) {
            return "未知用户";
        }
        UserEntity user = userMap.get(userId);
        if (user != null && user.getUsername() != null && !user.getUsername().trim().isEmpty()) {
            return user.getUsername();
        }
        return "用户#" + userId;
    }
}