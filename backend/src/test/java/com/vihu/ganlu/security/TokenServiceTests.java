package com.vihu.ganlu.security;

import com.vihu.ganlu.entitys.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenServiceTests {
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "secret", "test-token-secret-with-more-than-32-bytes");
        ReflectionTestUtils.setField(tokenService, "expirationSeconds", 3600L);
    }

    @Test
    void createsAndVerifiesSignedToken() {
        UserEntity user = new UserEntity();
        user.setId(42);

        String token = tokenService.createToken(user);

        assertEquals(42, tokenService.verifyAndGetUserId(token));
    }

    @Test
    void rejectsTamperedToken() {
        UserEntity user = new UserEntity();
        user.setId(42);
        String token = tokenService.createToken(user);

        assertThrows(RuntimeException.class,
                () -> tokenService.verifyAndGetUserId(token + "tampered"));
    }

    @Test
    void rejectsWeakSigningSecret() {
        ReflectionTestUtils.setField(tokenService, "secret", "too-short");

        assertThrows(IllegalStateException.class, tokenService::validateConfiguration);
    }
}
