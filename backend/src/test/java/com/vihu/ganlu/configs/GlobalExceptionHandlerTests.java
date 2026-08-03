package com.vihu.ganlu.configs;

import com.vihu.ganlu.utils.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTests {
    @Test
    void convertsLateForeignKeyConflictToReadableResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("foreign key conflict"));

        assertEquals(409, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("无法删除"));
    }
}
