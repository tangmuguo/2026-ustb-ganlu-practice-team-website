package com.vihu.ganlu.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.ai.AiChatRequest;
import com.vihu.ganlu.entitys.ai.AiChatResponse;
import com.vihu.ganlu.entitys.ai.AiMessageDto;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.service.AiService;
import com.vihu.ganlu.service.impl.AiServiceImpl.AiServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiActionTests {

    private AiAction aiAction;
    private AiService aiService;
    private HttpServletRequest httpRequest;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        aiService = mock(AiService.class);
        httpRequest = mock(HttpServletRequest.class);
        aiAction = new AiAction(aiService, httpRequest);
        mockMvc = MockMvcBuilders.standaloneSetup(aiAction).build();
    }

    // ---- 直接调用测试（已有） ----

    @Test
    void shouldReturnAnswerOnValidRequest() {
        UserEntity user = new UserEntity();
        user.setId(1);
        when(httpRequest.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE)).thenReturn(user);
        when(aiService.chat(any(AiChatRequest.class), eq(1)))
                .thenReturn(new AiChatResponse("你好，有什么可以帮你的？", "abc123def456"));

        AiChatRequest request = new AiChatRequest();
        request.setMessages(Arrays.asList(new AiMessageDto("user", "你好")));

        ResponseEntity<?> response = aiAction.chat(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo(200);
        assertThat(body.get("message")).isEqualTo("success");
        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) body.get("content");
        assertThat(content).isNotNull();
        assertThat(content.get("answer")).isEqualTo("你好，有什么可以帮你的？");
        assertThat(content.get("requestId")).isEqualTo("abc123def456");
    }

    @Test
    void shouldReturn503WithHttpStatusWhenServiceThrows() {
        when(httpRequest.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE)).thenReturn(null);
        when(aiService.chat(any(AiChatRequest.class), eq(null)))
                .thenThrow(new AiServiceException(503, "AI 服务未配置"));

        AiChatRequest request = new AiChatRequest();
        request.setMessages(Arrays.asList(new AiMessageDto("user", "hello")));

        ResponseEntity<?> response = aiAction.chat(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo(503);
        assertThat(body.get("content")).isNull();
    }

    @Test
    void shouldReturn400OnBadRequest() {
        when(httpRequest.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE)).thenReturn(null);
        when(aiService.chat(any(AiChatRequest.class), eq(null)))
                .thenThrow(new AiServiceException(400, "消息不能为空"));

        ResponseEntity<?> response = aiAction.chat(new AiChatRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn429OnRateLimit() {
        when(httpRequest.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE)).thenReturn(null);
        when(aiService.chat(any(AiChatRequest.class), eq(null)))
                .thenThrow(new AiServiceException(429, "请求较多"));

        ResponseEntity<?> response = aiAction.chat(new AiChatRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void chatMethodShouldHaveRequireRolesAnnotation() throws NoSuchMethodException {
        Method method = AiAction.class.getMethod("chat", AiChatRequest.class);
        RequireRoles annotation = method.getAnnotation(RequireRoles.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly(0, 1, 2);
    }

    @Test
    void shouldReturn400OnIllegalArgumentException() {
        when(httpRequest.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE)).thenReturn(null);
        when(aiService.chat(any(AiChatRequest.class), eq(null)))
                .thenThrow(new IllegalArgumentException("未知字段: extra"));

        ResponseEntity<?> response = aiAction.chat(new AiChatRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo(400);
        assertThat(body.get("message")).asString().contains("未知字段");
        assertThat(body.get("content")).isNull();
    }

    // ---- MockMvc 测试：真实 JSON 反序列化链路 ----

    @Test
    void shouldRejectTopLevelUnknownFieldWithJson400() throws Exception {
        // 发送含顶层未知字段 "extra" 的 JSON，验证 @JsonAnySetter → @ExceptionHandler 链路
        String json = "{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"extra\":\"unexpected\"}";

        mockMvc.perform(post("/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("未知字段: extra"))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void shouldRejectNestedUnknownFieldInMessageWithJson400() throws Exception {
        // 发送 messages[0] 中含未知字段 "foo" 的 JSON
        String json = "{\"messages\":[{\"role\":\"user\",\"content\":\"hi\",\"foo\":\"bar\"}]}";

        mockMvc.perform(post("/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("未知字段: foo"))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void shouldRejectMalformedJsonWith400() throws Exception {
        String json = "{bad json}";

        mockMvc.perform(post("/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.content").isEmpty());
    }
}
