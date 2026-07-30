package com.vihu.ganlu.actions;

import com.google.common.collect.ImmutableMap;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.ai.AiChatRequest;
import com.vihu.ganlu.entitys.ai.AiChatResponse;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.service.AiService;
import com.vihu.ganlu.service.impl.AiServiceImpl.AiServiceException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/ai")
public class AiAction {

    private final AiService aiService;
    private final HttpServletRequest httpRequest;

    public AiAction(AiService aiService, HttpServletRequest httpRequest) {
        this.aiService = aiService;
        this.httpRequest = httpRequest;
    }

    /**
     * AI 对话接口 —— 接收 { "messages": [{ "role":"user", "content":"..." }] }，
     * 返回 { "code":200, "message":"success", "content":{ "answer":"...", "requestId":"..." } }。
     * 管理员、团队账号、学生账号均可使用。
     */
    @RequireRoles({0, 1, 2})
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody AiChatRequest request) {
        UserEntity currentUser = (UserEntity) httpRequest.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE);
        Integer userId = currentUser != null ? currentUser.getId() : null;

        try {
            AiChatResponse response = aiService.chat(request, userId);
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "success",
                    "content", ImmutableMap.of(
                            "answer", response.getAnswer(),
                            "requestId", response.getRequestId()
                    )
            ));
        } catch (AiServiceException e) {
            int code = e.getCode();
            org.springframework.http.HttpStatus httpStatus = toHttpStatus(code);
            Map<String, Object> body = new HashMap<>();
            body.put("code", code);
            body.put("message", e.getMessage());
            body.put("content", null);
            return ResponseEntity.status(httpStatus).body(body);
        } catch (IllegalArgumentException e) {
            Map<String, Object> body = new HashMap<>();
            body.put("code", 400);
            body.put("message", e.getMessage());
            body.put("content", null);
            return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST).body(body);
        }
    }

    private static org.springframework.http.HttpStatus toHttpStatus(int code) {
        switch (code) {
            case 400: return org.springframework.http.HttpStatus.BAD_REQUEST;
            case 429: return org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
            case 502: return org.springframework.http.HttpStatus.BAD_GATEWAY;
            case 503: return org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
            case 504: return org.springframework.http.HttpStatus.GATEWAY_TIMEOUT;
            default:  return org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    /**
     * 处理 Jackson 反序列化异常（含 @JsonAnySetter 拒绝的未知字段），
     * 返回统一 JSON 错误结构 {code, message, content}。
     * 该异常发生在进入 chat() 之前，因此必须用 @ExceptionHandler 捕获。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleUnreadableMessage(HttpMessageNotReadableException e) {
        String msg = "请求格式错误";
        Throwable cause = e.getMostSpecificCause();
        if (cause instanceof IllegalArgumentException && cause.getMessage() != null) {
            msg = cause.getMessage();
        }
        Map<String, Object> body = new HashMap<>();
        body.put("code", 400);
        body.put("message", msg);
        body.put("content", null);
        return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST).body(body);
    }
}
