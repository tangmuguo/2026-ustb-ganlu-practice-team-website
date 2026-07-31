package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.context.AuthContext;
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

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MessageServiceImpl implements MessageService {

    @Autowired
    private MessageMapper messageMapper;
    @Autowired
    private ReplyMapper replyMapper;
    @Autowired
    private UserMapper userMapper;

    /**
     * 分页查询留言列表（游客开放，N+1完全优化）
     */
    @Override
    public Map<String, Object> getMessageList(int page, int pageSize) {
        // 参数边界校验
        if (page < 1) page = 1;
        if (pageSize < 1 || pageSize > 50) pageSize = 10;
        int offset = (page - 1) * pageSize;

        // 1. 查询有效留言 status=1
        List<MessageEntity> messages = messageMapper.selectPage(1, offset, pageSize);
        int total = messageMapper.countByStatus(1);

        Map<String, Object> result = new HashMap<>(8);
        // 优化点1：提前初始化返回对象，避免空列表重复new map
        result.put("messages", messages);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);

        if (messages.isEmpty()) {
            return result;
        }

        // 收集留言ID
        List<Integer> messageIdList = messages.stream()
                .map(MessageEntity::getId)
                .collect(Collectors.toList());

        // 2. 批量查询当前页所有有效回复 status=1
        List<ReplyEntity> allReplies = replyMapper.selectByMessageIds(messageIdList, 1);

        // 收集所有需要查询的用户ID（留言作者+回复作者）
        Set<Integer> userIdSet = new HashSet<>();
        messages.forEach(msg -> userIdSet.add(msg.getUserId()));
        allReplies.forEach(reply -> userIdSet.add(reply.getUserId()));
        List<Integer> userIdList = new ArrayList<>(userIdSet);

        Map<Integer, UserEntity> userMap = new HashMap<>();
        // 优化点2：判空！防止userIdList为空传入mapper导致in()语法异常
        if (!userIdList.isEmpty()) {
            List<UserEntity> userList = userMapper.selectUserByIdList(userIdList);
            userMap = userList.stream()
                    .collect(Collectors.toMap(
                            UserEntity::getId,
                            u -> u,
                            (exist, newVal) -> exist // 冲突合并策略，防止id重复报错
                    ));
        }

        // 回复按留言ID分组
        Map<Integer, List<ReplyEntity>> replyGroupMap = allReplies.stream()
                .collect(Collectors.groupingBy(ReplyEntity::getMessageId));

        // 组装数据
        for (MessageEntity msg : messages) {
            List<ReplyEntity> replyList = replyGroupMap.getOrDefault(msg.getId(), Collections.emptyList());
            msg.setReplies(replyList);

            // 填充留言发布人信息
            UserEntity msgUser = userMap.get(msg.getUserId());
            if (msgUser != null) {
                msg.setUsername(msgUser.getUsername());
                msg.setTeamname(msgUser.getTeamname());
            }

            // 填充每条回复发布人信息
            for (ReplyEntity reply : replyList) {
                UserEntity replyUser = userMap.get(reply.getUserId());
                if (replyUser != null) {
                    reply.setUsername(reply.getUsername());
                    reply.setTeamname(reply.getTeamname());
                }
            }
        }
        return result;
    }

    /**
     * 新增留言
     * @param content 留言内容
     */
    @Override
    public void addMessage(String content) {
        // 从上下文获取登录用户ID（禁止前端传参）
        Integer userId = AuthContext.getCurrentUserId();

        // 内容校验
        if (content == null) {
            throw new BadRequestException("内容不能为空");
        }
        content = content.trim();
        if (content.isEmpty()) {
            throw new BadRequestException("内容不能为空");
        }
        if (content.length() > 500) {
            throw new BadRequestException("内容不能超过500字");
        }

        // 构造实体入库
        MessageEntity message = new MessageEntity();
        message.setUserId(userId);
        message.setContent(content);
        message.setStatus(1);
        message.setCreateTime(new Date());
        messageMapper.insert(message);
    }

    /**
     * 新增回复
     * @param messageId 留言id
     * @param content 回复内容
     */
    @Override
    public void addReply(Integer messageId, String content) {
        Integer userId = AuthContext.getCurrentUserId();

        // 内容校验
        if (content == null) {
            throw new BadRequestException("内容不能为空");
        }
        content = content.trim();
        if (content.isEmpty()) {
            throw new BadRequestException("内容不能为空");
        }
        if (content.length() > 300) {
            throw new BadRequestException("回复不能超过300字");
        }

        // 校验留言存在且未删除
        MessageEntity message = messageMapper.selectById(messageId);
        if (message == null || !message.getStatus().equals(1)) {
            throw new NotFoundException("留言不存在");
        }

        // 新增回复
        ReplyEntity reply = new ReplyEntity();
        reply.setMessageId(messageId);
        reply.setUserId(userId);
        reply.setContent(content);
        reply.setStatus(1);
        reply.setCreateTime(new Date());
        replyMapper.insert(reply);
    }

    /**
     * 逻辑删除留言（仅level 0/1管理员）
     * @param id 留言ID
     */
    @Override
    public void deleteMessage(Integer id) {
        UserEntity loginUser = AuthContext.getCurrentUser();
        Integer level = loginUser.getLevel();

        // 权限校验：仅0、1级管理员可删除
        if (!level.equals(0) && !level.equals(1)) {
            throw new ForbiddenException("没有删除权限");
        }

        MessageEntity message = messageMapper.selectById(id);
        if (message == null) {
            throw new NotFoundException("留言不存在");
        }

        // 逻辑删除，status更新为0，不物理删除
        messageMapper.logicDeleteById(id);
    }

    /**
     * 逻辑删除回复（仅level 0/1管理员）
     * @param id 回复ID
     */
    @Override
    public void deleteReply(Integer id) {
        UserEntity loginUser = AuthContext.getCurrentUser();
        Integer level = loginUser.getLevel();

        if (!level.equals(0) && !level.equals(1)) {
            throw new ForbiddenException("没有删除权限");
        }

        ReplyEntity reply = replyMapper.selectById(id);
        if (reply == null) {
            throw new NotFoundException("回复不存在");
        }

        replyMapper.logicDeleteById(id);
    }
}