package com.vihu.ganlu.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.message.DeleteContentRequest;
import com.vihu.ganlu.entitys.message.MessageCreateRequest;
import com.vihu.ganlu.entitys.message.ReplyCreateRequest;
import com.vihu.ganlu.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 留言板接口集成测试
 * 覆盖：参数校验、权限校验、分页边界、增删场景、统一响应格式
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class MessageActionTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "secret", "test-token-secret-with-more-than-32-bytes");
        ReflectionTestUtils.setField(tokenService, "expirationSeconds", 3600L);
    }

    /**
     * 工具方法：生成指定用户的测试token
     */
    private String buildToken(int userId, int level) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLevel(level);
        return tokenService.createToken(user);
    }

    // ========== 分页查询测试 ==========

    @Test
    @DisplayName("分页查询-正常访问（游客可访问）")
    void testList_success_public() throws Exception {
        mockMvc.perform(get("/message/list")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.content").exists());
    }

    @Test
    @DisplayName("分页查询-失败：page=0 返回400")
    void testPageZero_should400() throws Exception {
        mockMvc.perform(get("/message/list")
                        .param("page", "0")
                        .param("pageSize", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.content").exists());
    }

    @Test
    @DisplayName("分页查询-失败：pageSize=1000 超出范围 返回400")
    void testPageSize1000_should400() throws Exception {
        mockMvc.perform(get("/message/list")
                        .param("page", "1")
                        .param("pageSize", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.content").exists());
    }

    // ========== 新增留言测试 ==========

    @Test
    @DisplayName("新增留言-成功（登录用户）")
    void testAddMessage_success() throws Exception {
        String token = buildToken(1002, 2);
        MessageCreateRequest request = new MessageCreateRequest();
        request.setContent("测试留言内容");

        mockMvc.perform(post("/message/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.content").exists());
    }

    @Test
    @DisplayName("新增留言-失败：空内容 返回400")
    void testAddMessage_emptyContent_should400() throws Exception {
        String token = buildToken(1002, 2);
        MessageCreateRequest request = new MessageCreateRequest();
        request.setContent("   ");

        mockMvc.perform(post("/message/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.content").exists());
    }

    @Test
    @DisplayName("新增留言-失败：内容超长501字 返回400")
    void testAddMessage_tooLongContent_should400() throws Exception {
        String token = buildToken(1002, 2);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 501; i++) {
            sb.append('a');
        }
        MessageCreateRequest request = new MessageCreateRequest();
        request.setContent(sb.toString());

        mockMvc.perform(post("/message/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.content").exists());
    }

    // ========== 新增回复测试 ==========

    @Test
    @DisplayName("新增回复-成功")
    void testAddReply_success() throws Exception {
        String token = buildToken(1002, 2);
        // 先新增一条留言
        MessageCreateRequest msg = new MessageCreateRequest();
        msg.setContent("测试留言");
        String msgResponse = mockMvc.perform(post("/message/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(msg)))
                .andReturn().getResponse().getContentAsString();
        Integer messageId = objectMapper.readTree(msgResponse).get("content").asInt();

        // 新增回复
        ReplyCreateRequest reply = new ReplyCreateRequest();
        reply.setMessageId(messageId);
        reply.setContent("测试回复");

        mockMvc.perform(post("/message/addReply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reply)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("新增回复-失败：空内容 返回400")
    void testAddReply_emptyContent_should400() throws Exception {
        String token = buildToken(1002, 2);
        ReplyCreateRequest request = new ReplyCreateRequest();
        request.setMessageId(1);
        request.setContent("   ");

        mockMvc.perform(post("/message/addReply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.content").exists());
    }

    @Test
    @DisplayName("新增回复-失败：内容超长301字 返回400")
    void testAddReply_tooLongContent_should400() throws Exception {
        String token = buildToken(1002, 2);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 301; i++) {
            sb.append('a');
        }
        ReplyCreateRequest request = new ReplyCreateRequest();
        request.setMessageId(1);
        request.setContent(sb.toString());

        mockMvc.perform(post("/message/addReply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.content").exists());
    }

    // ========== 删除留言测试 ==========

    @Test
    @DisplayName("删除留言-失败：不存在的ID 返回404")
    void testDeleteMessage_notExist_should404() throws Exception {
        String token = buildToken(1001, 1);
        DeleteContentRequest request = new DeleteContentRequest();
        request.setId(99999);

        mockMvc.perform(post("/message/deleteMessage")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.content").exists());
    }

    @Test
    @DisplayName("删除留言-失败：level2普通用户无权限 返回403")
    void testDelete_forgeUserId_still403() throws Exception {
        String token = buildToken(1002, 2);
        DeleteContentRequest request = new DeleteContentRequest();
        request.setId(1);

        mockMvc.perform(post("/message/deleteMessage")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.content").exists());
    }

    // ========== 删除回复测试 ==========

    @Test
    @DisplayName("删除回复-失败：不存在的ID 返回404")
    void testDeleteReply_notExist_should404() throws Exception {
        String token = buildToken(1001, 1);
        DeleteContentRequest request = new DeleteContentRequest();
        request.setId(99999);

        mockMvc.perform(post("/message/deleteReply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.content").exists());
    }

    @Test
    @DisplayName("删除回复-失败：level2普通用户无权限 返回403")
    void testDeleteReply_level2_forbidden() throws Exception {
        String token = buildToken(1002, 2);
        DeleteContentRequest request = new DeleteContentRequest();
        request.setId(1);

        mockMvc.perform(post("/message/deleteReply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.content").exists());
    }
}