package com.vihu.ganlu.actions;

import com.vihu.ganlu.entitys.TeamDetailDto;
import com.vihu.ganlu.entitys.TeamEntity;
import com.vihu.ganlu.entitys.TeamSaveRequest;
import com.vihu.ganlu.entitys.TeamYearSummary;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.TokenService;
import com.vihu.ganlu.service.TeamServie;
import com.vihu.ganlu.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamActionTests {
    private TeamServie teamServie;
    private UserService userService;
    private TokenService tokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        teamServie = mock(TeamServie.class);
        userService = mock(UserService.class);
        tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "secret", "test-token-secret-with-more-than-32-bytes");
        ReflectionTestUtils.setField(tokenService, "expirationSeconds", 3600L);

        AuthInterceptor interceptor = new AuthInterceptor(tokenService, userService);
        mockMvc = MockMvcBuilders.standaloneSetup(new TeamAction(teamServie))
                .addInterceptors(interceptor)
                .build();
    }

    @Test
    void yearsArePublicAndUseUnifiedResponse() throws Exception {
        TeamYearSummary summary = new TeamYearSummary();
        summary.setYear("2025");
        summary.setCoverUrl("/covers/2025.jpg");
        summary.setPublishedTeamCount(3);
        when(teamServie.getPublishedYears()).thenReturn(Collections.singletonList(summary));

        mockMvc.perform(get("/teams/years"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("查询成功"))
                .andExpect(jsonPath("$.content[0].year").value("2025"))
                .andExpect(jsonPath("$.content[0].publishedTeamCount").value(3));
    }

    @Test
    void unpublishedOrMissingTeamReturnsNotFound() throws Exception {
        when(teamServie.getPublishedTeamDetail(9)).thenReturn(null);

        mockMvc.perform(get("/teams/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("小队不存在或未发布"));
    }

    @Test
    void teamAccountCannotCreateTeam() throws Exception {
        UserEntity teamAccount = user(7, 1);
        when(userService.findUserById(7)).thenReturn(teamAccount);

        mockMvc.perform(post("/admin/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teamAccount))
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(teamServie);
    }

    @Test
    void administratorCanCreateTeam() throws Exception {
        UserEntity administrator = user(1, 0);
        when(userService.findUserById(1)).thenReturn(administrator);
        TeamDetailDto detail = new TeamDetailDto();
        detail.setId(10);
        detail.setYear("2025");
        detail.setName("星火小队");
        detail.setStatus(TeamEntity.Status.DRAFT);
        detail.setPageId(20);
        when(teamServie.createTeam(any(TeamSaveRequest.class))).thenReturn(detail);

        mockMvc.perform(post("/admin/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(administrator))
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.content.id").value(10))
                .andExpect(jsonPath("$.content.pageId").value(20));
    }

    @Test
    void duplicateTeamNameReturnsConflict() throws Exception {
        UserEntity administrator = user(1, 0);
        when(userService.findUserById(1)).thenReturn(administrator);
        when(teamServie.createTeam(any(TeamSaveRequest.class)))
                .thenThrow(new DuplicateKeyException("同一年份下已存在同名小队"));

        mockMvc.perform(post("/admin/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(administrator))
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("同一年份下已存在同名小队"));
    }

    @Test
    void ownerAlreadyBoundReturnsCorrectMessage() throws Exception {
        // service 层预检抛"负责人占用"中文 message，应原样透传（不再被误报为"重名"）
        UserEntity administrator = user(1, 0);
        when(userService.findUserById(1)).thenReturn(administrator);
        when(teamServie.createTeam(any(TeamSaveRequest.class)))
                .thenThrow(new DuplicateKeyException("该负责人账号已绑定其他小队"));

        mockMvc.perform(post("/admin/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(administrator))
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("该负责人账号已绑定其他小队"));
    }

    @Test
    void dbUniqueConstraintMappedToChinese() throws Exception {
        // 并发漏过 service 预检命中 DB uk_team_owner_user 时，MyBatis 抛底层技术文本
        // （含约束名），handler 应按约束名映射回中文，不泄露技术细节
        UserEntity administrator = user(1, 0);
        when(userService.findUserById(1)).thenReturn(administrator);
        Throwable dbCause = new RuntimeException(
                "### Error updating database; Duplicate entry '5' for key 'team.uk_team_owner_user'");
        when(teamServie.createTeam(any(TeamSaveRequest.class)))
                .thenThrow(new DuplicateKeyException("MyBatis 重抛", dbCause));

        mockMvc.perform(post("/admin/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(administrator))
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("该负责人账号已绑定其他小队"));
    }

    @Test
    void unknownConstraintReturnsGenericChineseNotRawSql() throws Exception {
        // F3 review: 命中未知约束（非 uk_team_owner_user/uk_team_year_name）且 message 是
        // 技术文本时，绝不返回原始 SQL，走通用中文兜底
        UserEntity administrator = user(1, 0);
        when(userService.findUserById(1)).thenReturn(administrator);
        Throwable dbCause = new RuntimeException(
                "### Error updating database; Duplicate entry 'x' for key 'team.uk_team_page_team_id'");
        when(teamServie.createTeam(any(TeamSaveRequest.class)))
                .thenThrow(new DuplicateKeyException("### Error updating database", dbCause));

        String message = mockMvc.perform(post("/admin/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(administrator))
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        // 断言返回通用中文，不含 "### Error" / "Duplicate entry" 等技术文本
        org.junit.jupiter.api.Assertions.assertTrue(
                !message.contains("### Error") && !message.contains("Duplicate entry"),
                "不应泄露技术 SQL 文本: " + message);
        org.junit.jupiter.api.Assertions.assertTrue(message.contains("数据冲突"),
                "应返回通用中文兜底: " + message);
    }

    private UserEntity user(int id, int level) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setLevel(level);
        return user;
    }

    private String bearer(UserEntity user) {
        return "Bearer " + tokenService.createToken(user);
    }

    private String validRequestJson() {
        return "{\"year\":\"2025\",\"name\":\"星火小队\","
                + "\"ownerUserId\":7,\"region\":\"甘肃陇南\","
                + "\"school\":\"希望小学\",\"status\":\"DRAFT\"}";
    }
}
