package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.mappers.UserMapper;
import com.vihu.ganlu.service.impl.UserServiceImpl;
import com.vihu.ganlu.utils.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserServiceSecurityTests {
    private UserMapper mapper;
    private PasswordEncoder encoder;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(UserMapper.class);
        encoder = new BCryptPasswordEncoder(4);
        service = new UserServiceImpl(mapper, encoder);
    }

    @Test
    void authenticatesBcryptPassword() {
        UserEntity user = user(1, "student", encoder.encode("secret123"));
        when(mapper.findUserByUsername("student")).thenReturn(user);

        assertSame(user, service.authenticate("student", "secret123"));
        verify(mapper, never()).updatePasswordById(anyInt(), anyString());
    }

    @Test
    void upgradesLegacyPlaintextAfterSuccessfulLogin() {
        UserEntity user = user(2, "legacy", "secret123");
        when(mapper.findUserByUsername("legacy")).thenReturn(user);

        assertSame(user, service.authenticate("legacy", "secret123"));
        ArgumentCaptor<String> password = ArgumentCaptor.forClass(String.class);
        verify(mapper).updatePasswordById(eq(2), password.capture());
        assertTrue(encoder.matches("secret123", password.getValue()));
    }

    @Test
    void doesNotUpgradeLegacyPasswordWhenLoginFails() {
        UserEntity user = user(3, "legacy", "secret123");
        when(mapper.findUserByUsername("legacy")).thenReturn(user);

        assertNull(service.authenticate("legacy", "wrong-password"));
        verify(mapper, never()).updatePasswordById(anyInt(), anyString());
    }

    @Test
    void hashesNewAccountPasswordBeforeInsert() {
        UserEntity user = user(null, "new-account", "secret123");
        user.setPhone("13800000000");
        when(mapper.countByUsername(anyString())).thenReturn(0);
        when(mapper.countByPhone(anyString())).thenReturn(0);
        when(mapper.addUser(user)).thenReturn(1);

        assertEquals(1, service.addUser(user));
        assertNotEquals("secret123", user.getPassword());
        assertTrue(encoder.matches("secret123", user.getPassword()));
    }

    @Test
    void rejectsDuplicateUsernameBeforeInsert() {
        UserEntity user = user(null, "duplicate", "secret123");
        when(mapper.countByUsername("duplicate")).thenReturn(1);

        assertThrows(ConflictException.class, () -> service.addUser(user));
        verify(mapper, never()).addUser(any());
    }

    private UserEntity user(Integer id, String username, String password) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(password);
        user.setLevel(2);
        return user;
    }
}
