package com.vihu.ganlu.actions;

import com.vihu.ganlu.configs.GlobalExceptionHandler;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.TokenService;
import com.vihu.ganlu.service.AuditEventService;
import com.vihu.ganlu.service.ContentReportService;
import com.vihu.ganlu.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.NoSuchElementException;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ContentReportActionTests {
    private ContentReportService reportService;
    private AuditEventService auditService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reportService = mock(ContentReportService.class);
        auditService = mock(AuditEventService.class);
        mockMvc = standaloneSetup(new ContentReportAction(reportService, auditService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void guestCanSubmitPublicTeamReportAndResponseContainsOnlyTicketId() throws Exception {
        when(reportService.create(any(), isNull(UserEntity.class))).thenReturn(88L);

        mockMvc.perform(post("/reports")
                        .contentType("application/json")
                        .content("{\"targetType\":\"TEAM_IMAGE\",\"targetId\":7,\"category\":\"OTHER\",\"description\":\"内容不当\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.content.ticketId", is(88)))
                .andExpect(jsonPath("$.content.reporterUserId").doesNotExist())
                .andExpect(jsonPath("$.content.phone").doesNotExist());

        verify(reportService).create(any(), isNull(UserEntity.class));
        verify(auditService).record(isNull(UserEntity.class), eq("CONTENT_REPORT_CREATE"),
                eq("TEAM_IMAGE"), eq(7), eq("SUCCESS"), eq("OTHER"));
    }

    @Test
    void loggedInReporterUsesCurrentUserAttributeWithoutAcceptingClientIdentity() throws Exception {
        UserEntity actor = user(19, 2);
        when(reportService.create(any(), eq(actor))).thenReturn(89L);

        mockMvc.perform(post("/reports")
                        .contentType("application/json")
                        .content("{\"targetType\":\"TEAM_WORD\",\"targetId\":8,\"category\":\"PRIVACY\"}")
                        .requestAttr(AuthInterceptor.CURRENT_USER_ATTRIBUTE, actor))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content.ticketId", is(89)))
                .andExpect(jsonPath("$.content.userId").doesNotExist());

        verify(reportService).create(any(), eq(actor));
    }

    @Test
    void invalidOrUnpublishedTargetReturnsNotFoundAndOnlyFailureAudit() throws Exception {
        when(reportService.create(any(), isNull(UserEntity.class)))
                .thenThrow(new NoSuchElementException("举报目标不存在或尚未公开"));

        mockMvc.perform(post("/reports")
                        .contentType("application/json")
                        .content("{\"targetType\":\"TEAM_MEDIA\",\"targetId\":31,\"category\":\"HARMFUL\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(404)))
                .andExpect(jsonPath("$.content").doesNotExist());

        verify(auditService).record(isNull(UserEntity.class), eq("CONTENT_REPORT_CREATE"),
                eq("TEAM_MEDIA"), eq(31), eq("FAILED"), eq("VALIDATION_FAILED"));
        verify(auditService, never()).record(isNull(UserEntity.class), eq("CONTENT_REPORT_CREATE"),
                eq("TEAM_MEDIA"), eq(31), eq("SUCCESS"), any());
    }

    @Test
    void adminResolutionRequiresAdminServicePathAndWritesSuccessAudit() throws Exception {
        UserEntity admin = user(1, 0);

        mockMvc.perform(put("/admin/reports/88")
                        .contentType("application/json")
                        .content("{\"status\":\"RESOLVED\",\"resolutionCode\":\"CONTENT_REMOVED\",\"resolutionNote\":\"违反公开内容规范\"}")
                        .requestAttr(AuthInterceptor.CURRENT_USER_ATTRIBUTE, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));

        verify(reportService).resolve(eq(88L), any(), eq(admin));
        verify(auditService).record(eq(admin), eq("CONTENT_REPORT_RESOLVE"),
                eq("CONTENT_REPORT"), eq(88L), eq("SUCCESS"), eq("RESOLVED_CONTENT_REMOVED"));
    }

    @Test
    void failedResolutionWritesFailureAudit() throws Exception {
        UserEntity admin = user(1, 0);
        doThrow(new NoSuchElementException("举报工单不存在"))
                .when(reportService).resolve(eq(99L), any(), eq(admin));

        mockMvc.perform(put("/admin/reports/99")
                        .contentType("application/json")
                        .content("{\"status\":\"REJECTED\",\"resolutionCode\":\"NO_VIOLATION\"}")
                        .requestAttr(AuthInterceptor.CURRENT_USER_ATTRIBUTE, admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(404)));

        verify(auditService).record(eq(admin), eq("CONTENT_REPORT_RESOLVE"),
                eq("CONTENT_REPORT"), eq(99L), eq("FAILED"), eq("VALIDATION_FAILED"));
    }

    @Test
    void publicEndpointAllowsGuestThroughAuthInterceptor() throws Exception {
        when(reportService.create(any(), isNull(UserEntity.class))).thenReturn(90L);
        TokenService tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "secret", "test-token-secret-with-more-than-32-bytes");
        ReflectionTestUtils.setField(tokenService, "expirationSeconds", 3600L);
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, mock(UserService.class));
        MockMvc protectedMvc = standaloneSetup(new ContentReportAction(reportService, auditService))
                .setControllerAdvice(new GlobalExceptionHandler()).addInterceptors(interceptor).build();

        protectedMvc.perform(post("/reports")
                        .contentType("application/json")
                        .content("{\"targetType\":\"TEAM_IMAGE\",\"targetId\":7,\"category\":\"OTHER\"}"))
                .andExpect(status().isCreated());
    }

    private UserEntity user(int id, int level) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setLevel(level);
        return user;
    }
}
