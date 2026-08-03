package com.vihu.ganlu.configs;

import com.vihu.ganlu.utils.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTests {
    @Test
    void keepsNonDeleteIntegrityConflictGeneric() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("foreign key conflict"));

        assertEquals(409, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().getCode());
        assertEquals("\u6570\u636e\u7ea6\u675f\u51b2\u7a81\uff0c\u8bf7\u68c0\u67e5\u5173\u8054\u8bb0\u5f55\u548c\u5fc5\u586b\u5b57\u6bb5",
                response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("\u5220\u9664"));
        assertFalse(response.getBody().getMessage().contains("\u8d26\u53f7"));
    }
}
