package com.vihu.ganlu.actions;

import com.vihu.ganlu.entitys.MessageEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.message.MessageCreateRequest;
import com.vihu.ganlu.configs.GlobalExceptionHandler;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.TokenService;
import com.vihu.ganlu.service.AuditEventService;
import com.vihu.ganlu.service.UserService;
import com.vihu.ganlu.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class MessageActionTests {
    private MessageServiceImpl messageService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        messageService = mock(MessageServiceImpl.class);
        MessageAction action = new MessageAction(messageService);
        mockMvc = standaloneSetup(action).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void testList_guest_shouldSuccess() throws Exception {
        Map<String, Object> content = new HashMap<>();
        content.put("messages", Collections.emptyList());
        content.put("total", 0);
        content.put("page", 1);
        content.put("pageSize", 10);
        when(messageService.getMessages(1, 10)).thenReturn(content);

        mockMvc.perform(get("/message/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.content.messages").isArray())
                .andExpect(jsonPath("$.content.total", is(0)))
                .andExpect(jsonPath("$.content.page", is(1)))
                .andExpect(jsonPath("$.content.pageSize", is(10)));
    }

    @Test
    void addMessage_ignoresForgedUserIdAndUsesCurrentUser() throws Exception {
        MessageEntity created = new MessageEntity();
        created.setId(9);
        when(messageService.addMessage(any(MessageCreateRequest.class), eq(2))).thenReturn(created);

        mockMvc.perform(post("/message/add")
                        .contentType("application/json")
                        .content("{\"content\":\"hello\",\"userId\":1}")
                        .requestAttr(AuthInterceptor.CURRENT_USER_ATTRIBUTE, user(2, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.content.id", is(9)));

        verify(messageService).addMessage(any(MessageCreateRequest.class), eq(2));
    }

    @Test
    void blockedContentDoesNotWriteSuccessAudit() throws Exception {
        AuditEventService auditService = mock(AuditEventService.class);
        MessageServiceImpl blockedService = mock(MessageServiceImpl.class);
        when(blockedService.addMessage(any(MessageCreateRequest.class), eq(2)))
                .thenThrow(new IllegalArgumentException("留言不能包含外链或URL"));
        MockMvc blockedMvc = standaloneSetup(new MessageAction(blockedService, auditService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        blockedMvc.perform(post("/message/add")
                        .contentType("application/json")
                        .content("{\"content\":\"请访问 https://example.com\"}")
                        .requestAttr(AuthInterceptor.CURRENT_USER_ATTRIBUTE, user(2, 2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.content").doesNotExist());

        verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void addMessage_guestShouldReturn401() throws Exception {
        mockMvcWithAuthInterceptor().perform(post("/message/add")
                        .contentType("application/json")
                        .content("{\"content\":\"hello\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is(401)));
    }

    @Test
    void addReply_guestShouldReturn401() throws Exception {
        mockMvcWithAuthInterceptor().perform(post("/message/addReply")
                        .contentType("application/json")
                        .content("{\"messageId\":1,\"content\":\"hello\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is(401)));
    }

    @Test
    void list_invalidPage_shouldReturn400() throws Exception {
        when(messageService.getMessages(0, 10))
                .thenThrow(new IllegalArgumentException("page必须大于等于1"));

        mockMvc.perform(get("/message/list?page=0&pageSize=10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", is("请求参数不正确")))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    void list_nonNumericPage_shouldReturn400ResponseBody() throws Exception {
        mockMvc.perform(get("/message/list?page=abc&pageSize=10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", is("请求参数不正确")))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    void addMessage_malformedJson_shouldReturn400ResponseBody() throws Exception {
        mockMvc.perform(post("/message/add")
                        .contentType("application/json")
                        .content("{")
                        .requestAttr(AuthInterceptor.CURRENT_USER_ATTRIBUTE, user(2, 2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", is("请求格式不正确")))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    void addMessage_unsupportedContentType_shouldReturn415ResponseBody() throws Exception {
        mockMvc.perform(post("/message/add")
                        .contentType("text/plain")
                        .content("hello")
                        .requestAttr(AuthInterceptor.CURRENT_USER_ATTRIBUTE, user(2, 2)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code", is(415)))
                .andExpect(jsonPath("$.message", is("不支持的请求类型")))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    void list_unexpectedExceptionWithoutMessage_shouldReturnStable500() throws Exception {
        when(messageService.getMessages(1, 10)).thenThrow(new RuntimeException());

        mockMvc.perform(get("/message/list"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code", is(500)))
                .andExpect(jsonPath("$.message", is("服务器暂时无法处理请求")))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    void deleteMessage_studentForbidden_shouldReturn403() throws Exception {
        doThrow(new SecurityException("无删除权限"))
                .when(messageService).deleteMessage(7, 3, null);

        mockMvc.perform(post("/message/deleteMessage")
                        .contentType("application/json")
                        .content("{\"id\":7,\"userId\":1}")
                        .requestAttr(AuthInterceptor.CURRENT_USER_ATTRIBUTE, user(3, 2)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));

        verify(messageService).deleteMessage(7, 3, null);
    }

    private UserEntity user(int id, int level) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setLevel(level);
        return user;
    }

    private MockMvc mockMvcWithAuthInterceptor() {
        MessageAction action = new MessageAction(messageService);
        TokenService tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "secret", "test-token-secret-with-more-than-32-bytes");
        ReflectionTestUtils.setField(tokenService, "expirationSeconds", 3600L);
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, mock(UserService.class));
        return standaloneSetup(action).setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(interceptor).build();
    }
}

