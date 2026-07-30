package com.vihu.ganlu.service;

import com.vihu.ganlu.configs.DeepSeekProperties;
import com.vihu.ganlu.entitys.ai.AiChatRequest;
import com.vihu.ganlu.entitys.ai.AiChatResponse;
import com.vihu.ganlu.entitys.ai.AiMessageDto;
import com.vihu.ganlu.service.impl.AiServiceImpl;
import com.vihu.ganlu.service.impl.AiServiceImpl.AiServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AiServiceTests {

    private AiService aiService;
    private MockRestServiceServer mockServer;
    private DeepSeekProperties properties;

    private static final Integer TEST_USER_ID = 1;

    @BeforeEach
    void setUp() {
        properties = new DeepSeekProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://api.deepseek.com");
        properties.setApiKey("sk-test-key-not-real");
        properties.setModel("deepseek-v4-flash");
        properties.setConnectTimeout(10000);
        properties.setReadTimeout(60000);

        RestTemplate restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofMillis(10000))
                .setReadTimeout(Duration.ofMillis(60000))
                .build();

        mockServer = MockRestServiceServer.createServer(restTemplate);
        aiService = new AiServiceImpl(properties, restTemplate);
    }

    private AiChatRequest req(AiMessageDto... messages) {
        AiChatRequest request = new AiChatRequest();
        request.setMessages(Arrays.asList(messages));
        return request;
    }

    private void mockDeepSeekResponse(String responseBody) {
        mockServer.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
    }

    @Test
    void shouldReturnAnswerOnSuccess() {
        mockDeepSeekResponse(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"光合作用是植物利用阳光合成养分的过程。\"}}]}");

        AiChatResponse response = aiService.chat(
                req(new AiMessageDto("user", "给三年级学生解释什么是光合作用")),
                TEST_USER_ID
        );

        assertThat(response.getAnswer()).contains("光合作用");
        assertThat(response.getRequestId()).isNotNull();
    }

    @Test
    void shouldRejectEmptyMessages() {
        AiChatRequest request = new AiChatRequest();
        request.setMessages(Collections.emptyList());

        assertThatThrownBy(() -> aiService.chat(request, TEST_USER_ID))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("消息不能为空");
    }

    @Test
    void shouldRejectTooManyMessages() {
        AiMessageDto[] arr = new AiMessageDto[21];
        for (int i = 0; i < 21; i++) {
            arr[i] = new AiMessageDto(i % 2 == 0 ? "user" : "assistant", "msg" + i);
        }

        assertThatThrownBy(() -> aiService.chat(req(arr), TEST_USER_ID))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("20");
    }

    @Test
    void shouldRejectOversizedMessage() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2001; i++) sb.append("a");

        assertThatThrownBy(() -> aiService.chat(
                req(new AiMessageDto("user", sb.toString())), TEST_USER_ID))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("2000");
    }

    @Test
    void shouldRejectInvalidRole() {
        assertThatThrownBy(() -> aiService.chat(
                req(new AiMessageDto("system", "hack")), TEST_USER_ID))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("不支持的消息角色");
    }

    @Test
    void shouldConvert401To502() {
        mockServer.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"error\":\"unauthorized\"}"));

        assertThatThrownBy(() -> aiService.chat(
                req(new AiMessageDto("user", "hello")), TEST_USER_ID))
                .isInstanceOf(AiServiceException.class)
                .matches(e -> ((AiServiceException) e).getCode() == 502);
    }

    @Test
    void shouldConvert429To429() {
        mockServer.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{}"));

        assertThatThrownBy(() -> aiService.chat(
                req(new AiMessageDto("user", "hello")), TEST_USER_ID))
                .isInstanceOf(AiServiceException.class)
                .matches(e -> ((AiServiceException) e).getCode() == 429);
    }

    @Test
    void shouldConvert5xxTo503() {
        mockServer.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withServerError().body("{}"));

        assertThatThrownBy(() -> aiService.chat(
                req(new AiMessageDto("user", "hello")), TEST_USER_ID))
                .isInstanceOf(AiServiceException.class)
                .matches(e -> ((AiServiceException) e).getCode() == 503);
    }

    @Test
    void shouldHandleEmptyChoices() {
        mockDeepSeekResponse("{\"choices\":[]}");

        AiChatResponse response = aiService.chat(
                req(new AiMessageDto("user", "hello")), TEST_USER_ID);
        assertThat(response.getAnswer()).contains("抱歉");
    }

    @Test
    void shouldHandleMissingContent() {
        mockDeepSeekResponse("{\"choices\":[{\"message\":{}}]}");

        AiChatResponse response = aiService.chat(
                req(new AiMessageDto("user", "hello")), TEST_USER_ID);
        assertThat(response.getAnswer()).contains("抱歉");
    }

    @Test
    void shouldReturn503WhenNotConfigured() {
        properties.setApiKey("");

        assertThatThrownBy(() -> aiService.chat(
                req(new AiMessageDto("user", "hello")), TEST_USER_ID))
                .isInstanceOf(AiServiceException.class)
                .matches(e -> ((AiServiceException) e).getCode() == 503);
    }

    @Test
    void shouldNotLeakApiKeyInResponse() {
        mockDeepSeekResponse(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}");

        AiChatResponse response = aiService.chat(
                req(new AiMessageDto("user", "hello")), TEST_USER_ID);
        assertThat(response.getAnswer()).doesNotContain("sk-");
        assertThat(response.getAnswer()).doesNotContain("test-key");
    }

    @Test
    void shouldRateLimitByUserId() {
        int limit = 30;
        // 前30次正常
        for (int i = 0; i < limit; i++) {
            mockDeepSeekResponse(
                    "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}");
        }
        for (int i = 0; i < limit; i++) {
            aiService.chat(req(new AiMessageDto("user", "msg" + i)), TEST_USER_ID);
        }

        // 第31次触发频率限制
        assertThatThrownBy(() -> aiService.chat(
                req(new AiMessageDto("user", "one too many")), TEST_USER_ID))
                .isInstanceOf(AiServiceException.class)
                .matches(e -> ((AiServiceException) e).getCode() == 429);
    }

    @Test
    void shouldRejectNullRequest() {
        assertThatThrownBy(() -> aiService.chat(null, TEST_USER_ID))
                .isInstanceOf(AiServiceException.class)
                .matches(e -> ((AiServiceException) e).getCode() == 400)
                .hasMessageContaining("请求不能为空");
    }

    @Test
    void shouldRejectNullMessageElement() {
        AiChatRequest request = new AiChatRequest();
        request.setMessages(Arrays.asList(null, new AiMessageDto("user", "ok")));

        assertThatThrownBy(() -> aiService.chat(request, TEST_USER_ID))
                .isInstanceOf(AiServiceException.class)
                .matches(e -> ((AiServiceException) e).getCode() == 400)
                .hasMessageContaining("不能为null");
    }

    @Test
    void shouldRejectBlankContent() {
        assertThatThrownBy(() -> aiService.chat(
                req(new AiMessageDto("user", "   ")), TEST_USER_ID))
                .isInstanceOf(AiServiceException.class)
                .matches(e -> ((AiServiceException) e).getCode() == 400);
    }

    @Test
    void shouldRejectNullRole() {
        assertThatThrownBy(() -> aiService.chat(
                req(new AiMessageDto(null, "hello")), TEST_USER_ID))
                .isInstanceOf(AiServiceException.class)
                .matches(e -> ((AiServiceException) e).getCode() == 400);
    }

    @Test
    void shouldConvertTimeoutTo504() {
        mockServer.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(request -> { throw new java.net.SocketTimeoutException("timeout"); });

        assertThatThrownBy(() -> aiService.chat(
                req(new AiMessageDto("user", "hello")), TEST_USER_ID))
                .isInstanceOf(AiServiceException.class)
                .matches(e -> ((AiServiceException) e).getCode() == 504);
    }

    @Test
    void shouldNotLogApiKeyOrSystemPromptInResponse() {
        mockDeepSeekResponse(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}");

        AiChatResponse response = aiService.chat(
                req(new AiMessageDto("user", "hello")), TEST_USER_ID);
        assertThat(response.getAnswer()).doesNotContain("sk-");
        assertThat(response.getAnswer()).doesNotContain("sk-test-key-not-real");
        // system 提示词不应出现在应答中
        assertThat(response.getAnswer()).doesNotContain("隐私");
    }

    @Test
    void shouldWorkWhenHmacKeyNotSet() {
        // 未设置 AI_LOG_HMAC_KEY 时，正常回答不应被阻断
        mockDeepSeekResponse(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}");
        AiChatResponse response = aiService.chat(
                req(new AiMessageDto("user", "test")), TEST_USER_ID);
        assertThat(response.getAnswer()).isEqualTo("ok");
    }

    @Test
    void shouldRejectMissingMessagesField() {
        AiChatRequest request = new AiChatRequest();
        assertThatThrownBy(() -> aiService.chat(request, TEST_USER_ID))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("消息不能为空");
    }
}
