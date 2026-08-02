package com.vihu.ganlu.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vihu.ganlu.entitys.message.MessageCreateRequest;
import com.vihu.ganlu.entitys.message.ReplyCreateRequest;
import com.vihu.ganlu.entitys.message.DeleteContentRequest;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.security.TokenService;
import com.vihu.ganlu.service.MessageService;
import com.vihu.ganlu.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MessageAction.class)
@Import(TokenService.class)
class MessageActionTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TokenService tokenService;

    @MockBean
    private MessageService messageService;

    @MockBean
    private UserService userService;

    @BeforeEach
    void setUp() {
        // 初始化 TokenService 配置
        ReflectionTestUtils.setField(tokenService, "secret", "test-token-secret-with-more-than-32-bytes");
        ReflectionTestUtils.setField(tokenService, "expirationSeconds", 3600L);
    }

    /**
     * 工具方法：生成指定用户的 token，并 mock 用户查询
     */
    private String loginUser(int userId, int level) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLevel(level);
        when(userService.findUserById(userId)).thenReturn(user);
        return tokenService.createToken(user);
    }

    /**
     * 工具方法：生成指定长度的字符串
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
    @DisplayName("新增留言-成功")
    void testAddMessage_success() throws Exception {
        String token = loginUser(1001, 2);
        // Mock Service 层返回
        when(messageService.addMessage(anyString(), anyInt())).thenReturn(1);

        MessageCreateRequest req = new MessageCreateRequest();
        req.setContent("测试留言内容");

        mockMvc.perform(post("/message/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.content").value(1));
    }

    @Test
    @DisplayName("新增留言-失败：空内容（@Valid校验）")
    void testAddMessage_emptyContent_should400() throws Exception {
        String token = loginUser(1001, 2);

        MessageCreateRequest req = new MessageCreateRequest();
        req.setContent("   ");

        mockMvc.perform(post("/message/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.content").exists());
    }

    @Test
    @DisplayName("新增留言-失败：内容超长（@Valid校验）")
    void testAddMessage_tooLongContent_should400() throws Exception {
        String token = loginUser(1001, 2);

        MessageCreateRequest req = new MessageCreateRequest();
        req.setContent(buildLongString(501));

        mockMvc.perform(post("/message/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.content").exists());
    }

    // ========== 分页查询测试 ==========

    @Test
    @DisplayName("分页查询-成功")
    void testList_success() throws Exception {
        // Mock Service 层返回分页数据
        Map<String, Object> pageData = new HashMap<>();
        pageData.put("total", 3);
        pageData.put("page", 1);
        pageData.put("messages", new Object[]{});
        when(messageService.getMessages(1, 10)).thenReturn(pageData);

        mockMvc.perform(get("/message/list")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.content.total").value(3));
    }

    @Test
    @DisplayName("分页查询-失败：page小于1")
    void testPageZero_should400() throws Exception {
        mockMvc.perform(get("/message/list")
                        .param("page", "0")
                        .param("pageSize", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.content").exists());
    }

    @Test
    @DisplayName("分页查询-失败：pageSize超出范围")
    void testPageSizeTooLarge_should400() throws Exception {
        mockMvc.perform(get("/message/list")
                        .param("page", "1")
                        .param("pageSize", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.content").exists());
    }

    // ========== 权限测试 ==========

    @Test
    @DisplayName("删除留言-失败：level=2无权限")
    void testDeleteMessage_level2_should403() throws Exception {
        String token = loginUser(2001, 2);

        DeleteContentRequest req = new DeleteContentRequest();
        req.setId(1);

        mockMvc.perform(post("/message/deleteMessage")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.content").exists());
    }

    @Test
    @DisplayName("删除回复-失败：level=2无权限")
    void testDeleteReply_level2_should403() throws Exception {
        String token = loginUser(2001, 2);

        DeleteContentRequest req = new DeleteContentRequest();
        req.setId(1);

        mockMvc.perform(post("/message/deleteReply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.content").exists());
    }
}