package com.vihu.ganlu.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.message.DeleteContentRequest;
import com.vihu.ganlu.entitys.message.MessageCreateRequest;
import com.vihu.ganlu.security.TokenService;

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

    @Autowired
    private TokenService tokenService;

    /**
     * 生成带 Bearer 前缀的有效 token
     * @param userId 用户ID（建议使用数据库中真实存在的用户ID）
     * @param level  用户等级
     */
    private String buildToken(int userId, int level) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setLevel(level);
        String token = tokenService.createToken(user);
        return "Bearer " + token;
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
        // 使用 level=2 的普通用户 token 认证
        String token = buildToken(2001, 2);

        // 恶意请求体：塞入 userId=1（管理员），试图越权删除
        String maliciousBody = "{\"id\":1,\"userId\":1}";

        mockMvc.perform(post("/message/delete")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("新增留言：请求体携带userId无效，以认证身份为准")
    void testAddMessage_forgeUserId_ignored() throws Exception {
        // 对应任务单：后端不信任请求体userId
        // 使用 level=2 的普通用户 token 认证
        String token = buildToken(2001, 2);
        // 请求体里伪造 userId=1（管理员）
        String maliciousBody = "{\"content\":\"测试留言\",\"userId\":1}";

        mockMvc.perform(post("/message/add")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousBody))
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
        String token = buildToken(1001, 1);

        // 1. 调用新增接口，造一条测试留言
        MessageCreateRequest addReq = new MessageCreateRequest();
        addReq.setContent("测试删除用的留言");
        String responseStr = mockMvc.perform(post("/message/add")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // ========== 关键：打印完整返回结果，排查结构 ==========
        System.out.println("===== 新增接口完整返回 =====");
        System.out.println(responseStr);
        System.out.println("===========================");

        // 2. 从返回结果里取留言ID（先按你原来的路径试，不对再改）
        Integer messageId = objectMapper.readTree(responseStr)
                .path("content")
                .path("id")
                .asInt();

        System.out.println("解析到的留言ID：" + messageId);

        // 3. 执行删除
        DeleteContentRequest req = new DeleteContentRequest();
        req.setId(messageId);

        mockMvc.perform(post("/message/delete")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("删除回复：level=2 学生返回403")
    void testDeleteReply_level2_forbidden() throws Exception {
        // 使用 level=2 的普通用户 token 认证
        String token = buildToken(2001, 2);

        DeleteContentRequest req = new DeleteContentRequest();
        req.setId(1);

        mockMvc.perform(post("/message/deleteReply")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}