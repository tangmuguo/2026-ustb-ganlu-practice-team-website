package com.vihu.ganlu.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.message.DeleteContentRequest;
import com.vihu.ganlu.entitys.message.MessageCreateRequest;
import com.vihu.ganlu.security.AuthInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 留言板接口层测试
 * 对应任务单必测场景：HTTP状态码、越权防护、参数边界、游客权限
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MessageActionTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 工具方法：构造测试用户
     */
    private UserEntity buildTestUser(int level, int userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLevel(level);
        return user;
    }

    // ========== 游客权限测试 ==========

    @Test
    @DisplayName("游客访问列表-成功200")
    void testList_guest_shouldSuccess() throws Exception {
        // 对应任务单：GET /message/list 对游客开放
        mockMvc.perform(get("/message/list")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("游客新增留言-返回401")
    void testAddMessage_guest_should401() throws Exception {
        // 对应任务单：游客新增返回401
        MessageCreateRequest req = new MessageCreateRequest();
        req.setContent("游客留言");

        mockMvc.perform(post("/message/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("游客新增回复-返回401")
    void testAddReply_guest_should401() throws Exception {
        // 对应任务单：游客回复返回401
        String body = "{\"messageId\":1,\"content\":\"游客回复\"}";

        mockMvc.perform(post("/message/addReply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ========== 安全测试：伪造用户ID ==========

    @Test
    @DisplayName("核心安全：level2用户伪造管理员ID删除，仍返回403")
    void testDelete_forgeUserId_still403() throws Exception {
        // 对应任务单：请求体伪造有权用户ID不能越权
        UserEntity level2User = buildTestUser(2, 2001);

        // 恶意请求体：塞入 userId=1（管理员），试图越权删除
        String maliciousBody = "{\"id\":1,\"userId\":1}";

        mockMvc.perform(post("/message/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousBody)
                        // 模拟拦截器已认证：当前登录用户是level2
                        .requestAttr(AuthInterceptor.CURRENT_USER_ATTRIBUTE, level2User))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("新增留言：请求体携带userId无效，以认证身份为准")
    void testAddMessage_forgeUserId_ignored() throws Exception {
        // 对应任务单：后端不信任请求体userId
        UserEntity level2User = buildTestUser(2, 2001);
        // 请求体里伪造 userId=1（管理员）
        String maliciousBody = "{\"content\":\"测试留言\",\"userId\":1}";

        mockMvc.perform(post("/message/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousBody)
                        .requestAttr(AuthInterceptor.CURRENT_USER_ATTRIBUTE, level2User))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        // 实际入库的userId是2001，不是伪造的1，Service层已保证
    }

    // ========== 参数边界测试 ==========

    @Test
    @DisplayName("分页参数：page=0 返回400")
    void testPageZero_should400() throws Exception {
        // 对应任务单：非法分页参数返回400
        mockMvc.perform(get("/message/list")
                        .param("page", "0")
                        .param("pageSize", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("分页参数：pageSize=1000 返回400")
    void testPageSize1000_should400() throws Exception {
        // 对应任务单：pageSize限制1~50，防止大页拖垮数据库
        mockMvc.perform(get("/message/list")
                        .param("page", "1")
                        .param("pageSize", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ========== 删除接口权限测试 ==========

    @Test
    @DisplayName("删除留言：level=1 管理员成功")
    void testDeleteMessage_level1_success() throws Exception {
        UserEntity admin = buildTestUser(1, 1001);
        DeleteContentRequest req = new DeleteContentRequest();
        req.setId(1);

        mockMvc.perform(post("/message/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .requestAttr(AuthInterceptor.CURRENT_USER_ATTRIBUTE, admin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("删除回复：level=2 学生返回403")
    void testDeleteReply_level2_forbidden() throws Exception {
        UserEntity student = buildTestUser(2, 2001);
        DeleteContentRequest req = new DeleteContentRequest();
        req.setId(1);

        mockMvc.perform(post("/message/deleteReply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .requestAttr(AuthInterceptor.CURRENT_USER_ATTRIBUTE, student))
                .andExpect(status().isForbidden());
    }
}