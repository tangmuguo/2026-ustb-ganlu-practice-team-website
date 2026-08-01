package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.MessageEntity;
import com.vihu.ganlu.entitys.ReplyEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.exception.BadRequestException;
import com.vihu.ganlu.exception.ForbiddenException;
import com.vihu.ganlu.exception.NotFoundException;
import com.vihu.ganlu.mappers.MessageMapper;
import com.vihu.ganlu.mappers.ReplyMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 留言板业务逻辑测试
 * 对应任务单必测场景：参数校验、权限校验、分页边界、逻辑删除、N+1优化
 */
@SpringBootTest
@Transactional // 测试完成自动回滚，不污染数据库
class MessageServiceTests {

    @Autowired
    private MessageService messageService;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private ReplyMapper replyMapper;

    /**
     * 工具方法：构造测试用户
     */
    private UserEntity buildUser(int level, Integer userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLevel(level);
        user.setUsername("测试用户" + level);
        return user;
    }

    /**
     * 工具方法：生成指定长度的字符串（JDK8兼容）
     */
    private String buildLongString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append('a');
        }
        return sb.toString();
    }

    // ========== 新增留言测试 ==========

    @Test
    @DisplayName("新增留言-成功（level=2 学生用户）")
    void testAddMessage_success_level2() {
        // 对应任务单：管理员、团队、学生都能新增留言
        assertDoesNotThrow(() -> messageService.addMessage("测试留言内容",1002));
    }

    @Test
    @DisplayName("新增留言-失败：空内容")
    void testAddMessage_emptyContent_shouldFail() {
        // 对应任务单：空白内容返回400
        assertThrows(BadRequestException.class,
                () -> messageService.addMessage("   ", 1002));
    }

    @Test
    @DisplayName("新增留言-失败：内容超长（501字）")
    void testAddMessage_tooLong_shouldFail() {
        // 对应任务单：超长内容返回400
        String longContent = buildLongString(501);
        assertThrows(BadRequestException.class,
                () -> messageService.addMessage(longContent, 1002));
    }

    // ========== 新增回复测试 ==========

    @Test
    @DisplayName("新增回复-成功")
    void testAddReply_success() {
        // 先通过业务方法新增一条留言，拿到真实自增ID
        Integer messageId = messageService.addMessage("测试留言", 1001);
        // 用真实ID新增回复，断言不抛出异常
        assertDoesNotThrow(() -> messageService.addReply(messageId, "测试回复内容", 2001));
    }

    @Test
    @DisplayName("新增回复-失败：留言已删除")
    void testAddReply_deletedMessage_shouldFail() {
        // 对应任务单：已删除留言不能回复，返回404
        UserEntity admin = buildUser(1, 1001);
        // 先新增留言，拿到真实ID
        Integer messageId = messageService.addMessage("即将删除的留言", 1002);
        // 用真实ID删除留言
        messageService.deleteMessage(messageId, admin);

        // 用已删除的留言ID新增回复，预期抛异常
        assertThrows(NotFoundException.class,
                () -> messageService.addReply(messageId, "回复内容", 1002));
    }


    @Test
    @DisplayName("新增回复-失败：内容超长（301字）")
    void testAddReply_tooLong_shouldFail() {
        // 先造一条正常留言
        Integer messageId = messageService.addMessage("测试留言", 1001);
        // 对应任务单：回复1~300字
        String longContent = buildLongString(301);
        assertThrows(BadRequestException.class,
                () -> messageService.addReply(messageId, longContent, 1002));
    }

    // ========== 删除权限测试 ==========

    @Test
    @DisplayName("删除留言-失败：level=2 无权限")
    void testDeleteMessage_level2_shouldForbidden() {
        // 对应任务单：level=2 删除返回403
        UserEntity level2User = buildUser(2, 1002);
        Integer messageId = messageService.addMessage("待删除留言", 1001);

        assertThrows(ForbiddenException.class,
                () -> messageService.deleteMessage(messageId, level2User));
    }

    @Test
    @DisplayName("删除留言-成功：level=1 管理员")
    void testDeleteMessage_level1_shouldSuccess() {
        // 对应任务单：level 0/1 可删除
        UserEntity level1User = buildUser(1, 1001);
        Integer messageId = messageService.addMessage("待删留言", 1002);

        assertDoesNotThrow(() -> messageService.deleteMessage(messageId, level1User));
    }

    @Test
    @DisplayName("删除回复-失败：level=2 无权限")
    void testDeleteReply_level2_shouldForbidden() {
        // 对应任务单：level=2 不能删除回复
        UserEntity level2User = buildUser(2, 1002);
        // 1. 先造一条留言
        Integer messageId = messageService.addMessage("测试留言", 1001);
        // 2. 造一条回复（通过Mapper插入，拿到回复自增ID）
        ReplyEntity reply = new ReplyEntity();
        reply.setMessageId(messageId);
        reply.setUserId(1002);
        reply.setContent("测试回复");
        reply.setStatus(1);
        reply.setCreateTime(new Date());
        replyMapper.insert(reply);

        assertThrows(ForbiddenException.class,
                () -> messageService.deleteReply(reply.getId(), level2User));
    }

    // ========== 分页测试 ==========

    @Test
    @DisplayName("分页校验：11条数据 pageSize=10 分页正确")
    void testPagination_11items_pageSize10() {
        // 对应任务单：11条数据、pageSize=10时总数和第二页正确
        for (int i = 0; i < 11; i++) {
            messageService.addMessage("分页测试留言" + i, 1001);
        }

        // 第1页：10条
        Map<String, Object> page1 = messageService.getMessages(1, 10);
        assertEquals(11, page1.get("total"));
        assertEquals(1, page1.get("page"));
        assertEquals(10, ((List<?>) page1.get("messages")).size());

        // 第2页：1条
        Map<String, Object> page2 = messageService.getMessages(2, 10);
        assertEquals(2, page2.get("page"));
        assertEquals(1, ((List<?>) page2.get("messages")).size());
    }

    // ========== 逻辑删除验证 ==========

    @Test
    @DisplayName("逻辑删除验证：删除后列表不可见")
    void testLogicDelete_notVisibleInList() {
        // 对应任务单：逻辑删除，公开列表不再显示
        UserEntity admin = buildUser(1, 1001);
        Integer msg1Id = messageService.addMessage("测试留言1", 1002);
        messageService.addMessage("测试留言2", 1002);

        Map<String, Object> before = messageService.getMessages(1, 10);
        int beforeTotal = (int) before.get("total");

        // 删除第一条留言
        messageService.deleteMessage(msg1Id, admin);

        Map<String, Object> after = messageService.getMessages(1, 10);
        int afterTotal = (int) after.get("total");
        assertEquals(beforeTotal - 1, afterTotal);
    }
}