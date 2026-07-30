package com.vihu.ganlu.actions;

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
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiActionTests {

    private AiAction aiAction;
    private AiService aiService;
    private HttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        aiService = mock(AiService.class);
        httpRequest = mock(HttpServletRequest.class);
        aiAction = new AiAction(aiService, httpRequest);
    }

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
}
