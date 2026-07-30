package com.vihu.ganlu.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vihu.ganlu.configs.DeepSeekProperties;
import com.vihu.ganlu.entitys.ai.AiChatRequest;
import com.vihu.ganlu.entitys.ai.AiChatResponse;
import com.vihu.ganlu.entitys.ai.AiMessageDto;
import com.vihu.ganlu.service.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * AI 服务实现 —— DeepSeek 服务端代理。
 */
@Service
public class AiServiceImpl implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);

    /**
     * 固定 system 提示词：甘露支教学习助手，适合儿童和教师。
     */
    private static final String SYSTEM_PROMPT =
            "你是\u201c甘露支教\u201d网站的AI学习助手。你的回答面向支教教师和小学生，请使用简洁易懂、友好温和的表达。" +
            "优先依据站内提供的上下文信息回答问题；如果上下文不足以回答，请明确说明并给出可行的建议。" +
            "重要安全提醒：你绝不能处理或记录学生的姓名、电话、家庭住址、身份证号等个人隐私信息。" +
            "如果用户问题中包含此类敏感信息，请忽略敏感内容并提醒用户不要在对话中透露隐私。";

    private static final int MAX_MESSAGES = 20;
    private static final int MAX_CONTENT_LENGTH = 2000;

    /** 单用户频率限制：每个用户每分钟最多 30 次请求，正常对话不受影响 */
    private static final int RATE_LIMIT_MAX_REQUESTS = 30;
    private static final long RATE_LIMIT_WINDOW_MS = 60_000;

    private final ConcurrentHashMap<Integer, RateLimitWindow> rateLimitMap = new ConcurrentHashMap<>();

    private final DeepSeekProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiServiceImpl(DeepSeekProperties properties, RestTemplate aiRestTemplate) {
        this.properties = properties;
        this.restTemplate = aiRestTemplate;
    }

    /** 日志匿名化 HMAC 密钥 —— 由环境变量注入，缺失时安全降级为 anon */
    public static volatile String hmacKey = System.getenv("AI_LOG_HMAC_KEY");

    /** HmacSHA256 算法名 */
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * 使用 HMAC/SHA-256 生成用户匿名标识。
     * 密钥从环境变量 AI_LOG_HMAC_KEY 读取，缺失时统一返回 "anon"（不记录可关联标识）。
     */
    public static String anonymize(Integer userId) {
        if (userId == null || hmacKey == null || hmacKey.isEmpty()) return "anon";
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    hmacKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hmac = mac.doFinal(userId.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", hmac[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return "anon";
        }
    }

    @Override
    public AiChatResponse chat(AiChatRequest request, Integer userId) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        long start = System.currentTimeMillis();
        String uid = anonymize(userId);

        // 0. 频率限制检查
        if (userId != null) {
            checkRateLimit(userId, requestId, uid);
        }

        // 1. 校验 AI 是否已配置
        if (!properties.isConfigured()) {
            log.warn("[ai-req={}][user={}] ai-not-configured", requestId, uid);
            throw new AiServiceException(503, "AI 服务未配置，请联系管理员设置 DeepSeek API Key。");
        }

        // 2. 校验请求和消息
        validateRequest(request, requestId, uid);

        // 3. 构建 DeepSeek 请求体
        List<Map<String, String>> payloadMessages = buildPayloadMessages(request.getMessages());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("temperature", 0.6);
        payload.put("messages", payloadMessages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + properties.getApiKey());

        log.info("[ai-req={}][user={}] start msgCount={}", requestId, uid, payloadMessages.size());

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    properties.resolveEndpoint(), HttpMethod.POST, entity, String.class);

            String answer = parseAnswer(response.getBody(), requestId);
            long elapsed = System.currentTimeMillis() - start;
            log.info("[ai-req={}][user={}] success elapsed={}ms", requestId, uid, elapsed);

            if (!StringUtils.hasText(answer)) {
                answer = "抱歉，我暂时没有生成有效回答，请稍后重试。";
            }

            return new AiChatResponse(answer, requestId);

        } catch (HttpClientErrorException e) {
            long elapsed = System.currentTimeMillis() - start;
            HttpStatus status = e.getStatusCode();
            log.warn("[ai-req={}][user={}] upstream-err status={} elapsed={}ms", requestId, uid, status.value(), elapsed);

            if (status == HttpStatus.UNAUTHORIZED) {
                throw new AiServiceException(502, "AI 服务认证失败，请联系管理员检查 API Key 配置。");
            } else if (status == HttpStatus.TOO_MANY_REQUESTS) {
                throw new AiServiceException(429, "AI 请求较多，请稍后再试。");
            }
            throw new AiServiceException(502, "AI 上游返回错误，请稍后重试。");

        } catch (HttpServerErrorException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[ai-req={}][user={}] upstream-5xx status={} elapsed={}ms", requestId, uid,
                    e.getStatusCode().value(), elapsed);
            throw new AiServiceException(503, "AI 服务暂时不可用，请稍后重试。");

        } catch (ResourceAccessException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[ai-req={}][user={}] timeout elapsed={}ms", requestId, uid, elapsed);
            throw new AiServiceException(504, "AI 服务响应超时，请稍后重试。");
        }
    }

    // ---- 请求校验 ----

    private void validateRequest(AiChatRequest request, String requestId, String uid) {
        if (request == null) {
            log.warn("[ai-req={}][user={}] request-null", requestId, uid);
            throw new AiServiceException(400, "请求不能为空");
        }
        List<AiMessageDto> messages = request.getMessages();
        if (messages == null || messages.isEmpty()) {
            throw new AiServiceException(400, "消息不能为空");
        }
        if (messages.size() > MAX_MESSAGES) {
            throw new AiServiceException(400, "消息数量不能超过 " + MAX_MESSAGES + " 条");
        }
        for (int i = 0; i < messages.size(); i++) {
            AiMessageDto msg = messages.get(i);
            if (msg == null) {
                throw new AiServiceException(400, "第" + (i + 1) + "条消息不能为null");
            }
            String role = msg.getRole();
            String content = msg.getContent();
            if (role == null || (!"user".equals(role) && !"assistant".equals(role))) {
                throw new AiServiceException(400, "不支持的消息角色: " + role);
            }
            if (content == null || content.trim().isEmpty()) {
                throw new AiServiceException(400, "消息内容不能为空");
            }
            if (content.length() > MAX_CONTENT_LENGTH) {
                throw new AiServiceException(400, "单条消息不能超过 " + MAX_CONTENT_LENGTH + " 字");
            }
        }
    }

    // ---- 构建请求体 ----

    private List<Map<String, String>> buildPayloadMessages(List<AiMessageDto> clientMessages) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(createMessage("system", SYSTEM_PROMPT));

        int totalChars = 0;
        for (AiMessageDto msg : clientMessages) {
            String role = msg.getRole();
            String content = msg.getContent();
            // 校验已在 validateRequest 中完成，这里直接构造
            totalChars += content.length();
            messages.add(createMessage(role, content));
        }

        if (totalChars > 32000) {
            throw new AiServiceException(400, "对话总字数超出限制，请精简问题或开始新对话。");
        }
        return messages;
    }

    private Map<String, String> createMessage(String role, String content) {
        Map<String, String> msg = new LinkedHashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }

    private String parseAnswer(String responseBody, String requestId) {
        try {
            if (!StringUtils.hasText(responseBody)) {
                return "";
            }
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.size() == 0) {
                return "";
            }
            JsonNode contentNode = choices.get(0).path("message").path("content");
            return contentNode.isMissingNode() ? "" : contentNode.asText("");
        } catch (Exception e) {
            log.warn("[ai-req={}] parse-error", requestId);
            return "";
        }
    }

    // ---- 频率限制 ----

    /**
     * 检查单用户频率限制，超限抛出 AiServiceException(429)。
     * 每分钟自动清理过期窗口。
     */
    private void checkRateLimit(Integer userId, String requestId, String uid) {
        long now = System.currentTimeMillis();
        RateLimitWindow window = rateLimitMap.compute(userId, (key, prev) -> {
            if (prev == null || now - prev.windowStart > RATE_LIMIT_WINDOW_MS) {
                return new RateLimitWindow(now, 1);
            }
            prev.count++;
            return prev;
        });

        if (window != null && window.count > RATE_LIMIT_MAX_REQUESTS) {
            log.warn("[ai-req={}][user={}] rate-limited count={}", requestId, uid, window.count);
            throw new AiServiceException(429, "请求较多，请稍后再试。");
        }

        // 惰性清理：移除超过 5 分钟未使用的条目
        if (rateLimitMap.size() > 1000 && now % 10 == 0) {
            long threshold = now - 300_000;
            rateLimitMap.entrySet().removeIf(e -> e.getValue().windowStart < threshold);
        }
    }

    private static class RateLimitWindow {
        final long windowStart;
        int count;

        RateLimitWindow(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }

    // ---- 内部异常 ----

    /**
     * AI 服务业务异常，Controller 层统一转换为 {code, message} 响应。
     */
    public static class AiServiceException extends RuntimeException {
        private final int code;

        public AiServiceException(int code, String message) {
            super(message);
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }
}
