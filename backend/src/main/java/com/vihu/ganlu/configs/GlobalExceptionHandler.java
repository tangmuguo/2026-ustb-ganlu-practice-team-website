package com.vihu.ganlu.configs;

import com.google.common.collect.ImmutableMap;
import com.vihu.ganlu.exception.BadRequestException;
import com.vihu.ganlu.exception.ForbiddenException;
import com.vihu.ganlu.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ImmutableMap.of(
                "code", 400,
                "message", e.getMessage(),
                "content", null
        ));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ImmutableMap.of(
                "code", 403,
                "message", e.getMessage(),
                "content", null
        ));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ImmutableMap.of(
                "code", 404,
                "message", e.getMessage(),
                "content", null
        ));
    }
}