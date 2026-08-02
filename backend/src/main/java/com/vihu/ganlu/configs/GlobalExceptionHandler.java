package com.vihu.ganlu.configs;

import com.vihu.ganlu.exception.BadRequestException;
import com.vihu.ganlu.exception.ForbiddenException;
import com.vihu.ganlu.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 构建统一响应体，content 固定为空字符串，保证字段一定存在，兼容 non_null 序列化配置
     */
    private Map<String, Object> buildResult(int code, String message) {
        Map<String, Object> result = new HashMap<>(3);
        result.put("code", code);
        result.put("message", message);
        result.put("content", "");
        return result;
    }

    // ========== 自定义业务异常 ==========
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildResult(400, e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(buildResult(403, e.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildResult(404, e.getMessage()));
    }

    // ========== 参数校验类异常 ==========
    /**
     * 处理 @Valid 请求体校验失败（POST JSON 参数）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("参数校验失败");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildResult(400, errorMsg));
    }

    /**
     * 处理 URL 参数 / 方法参数校验失败（GET 请求参数 @Min/@Max 等）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException e) {
        String errorMsg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildResult(400, errorMsg));
    }

    /**
     * 处理参数类型不匹配（比如 page 传了非数字字符串）
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildResult(400, "参数类型错误"));
    }

    /**
     * 处理缺少必填参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildResult(400, "缺少必填参数: " + e.getParameterName()));
    }

    // ========== 全局兜底异常 ==========
    /**
     * 兜底所有未处理的异常，统一返回格式，避免返回 Spring 默认错误页
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception e) {
        // 打印异常栈，方便排查问题
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildResult(500, "服务器内部错误"));
    }
}