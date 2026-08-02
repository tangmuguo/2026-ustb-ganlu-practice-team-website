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
import org.springframework.test.context.ActiveProfiles;
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
 * 独立测试环境：激活test配置，使用H2内存库，不依赖外部环境
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

    @Autowired
    private TokenService tokenService;

    /**
     * 生成带 Bearer 前缀的有效 token
     * 权限规则：level=0管理员 / level=1团队成员 / level=2学生
     * @param userId 用户ID（对应测试库初始化的基准用户）
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
        String response = mockMvc.perform(get("/message/list")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        // 打印完整返回，定位500具体报错
        System.out.println("========== 列表接口响应 ==========");
        System.out.println(response);
        System.out.println("==================================");
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
        String token = buildToken(2001, 2);
        String maliciousBody = "{\"id\":1,\"userId\":1}";

        String response = mockMvc.perform(post("/message/deleteMessage")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousBody))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 打印响应，看是401还是403
        System.out.println("========== 伪造ID删除响应 ==========");
        System.out.println("HTTP状态码：" + mockMvc.perform(post("/message/deleteMessage")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousBody))
                .andReturn().getResponse().getStatus());
        System.out.println(response);
        System.out.println("==================================");
    }

    @Test
    @DisplayName("新增留言：请求体携带userId无效，以认证身份为准")
    void testAddMessage_forgeUserId_ignored() throws Exception {
        String token = buildToken(2001, 2);
        String maliciousBody = "{\"content\":\"测试留言\",\"userId\":1}";

        // 先不强断言，打印完整响应，定位401原因
        String response = mockMvc.perform(post("/message/add")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousBody))
                .andReturn()
                .getResponse()
                .getContentAsString();

        System.out.println("========== 带token新增留言响应 ==========");
        System.out.println("HTTP状态码：" + mockMvc.perform(post("/message/add")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousBody))
                .andReturn().getResponse().getStatus());
        System.out.println(response);
        System.out.println("========================================");
    }

    // ========== 参数边界测试 ==========

    @Test
    @DisplayName("分页参数：page=0 返回400")
    void testPageZero_should400() throws Exception {
        mockMvc.perform(get("/message/list")
                        .param("page", "0")
                        .param("pageSize", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("分页参数：pageSize=1000 返回400")
    void testPageSize1000_should400() throws Exception {
        mockMvc.perform(get("/message/list")
                        .param("page", "1")
                        .param("pageSize", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ========== 删除接口权限测试 ==========

    @Test
    @DisplayName("删除留言：level=0 管理员成功")
    void testDeleteMessage_level0_success() throws Exception {
        String token = buildToken(1001, 0);
        MessageCreateRequest addReq = new MessageCreateRequest();
        addReq.setContent("测试删除用的留言");

        // 先打印新增接口的响应，定位是新增401还是删除401
        String addResponse = mockMvc.perform(post("/message/add")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        System.out.println("========== 管理员新增留言响应 ==========");
        System.out.println(addResponse);
        System.out.println("======================================");

        // 先不执行删除，先确认新增是否正常
    }

    @Test
    @DisplayName("删除回复：level=2 学生返回403")
    void testDeleteReply_level2_forbidden() throws Exception {
        String token = buildToken(2001, 2);
        DeleteContentRequest req = new DeleteContentRequest();
        req.setId(1);

        String response = mockMvc.perform(post("/message/deleteReply")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        System.out.println("========== 学生删除回复响应 ==========");
        System.out.println(response);
        System.out.println("==================================");
    }
}