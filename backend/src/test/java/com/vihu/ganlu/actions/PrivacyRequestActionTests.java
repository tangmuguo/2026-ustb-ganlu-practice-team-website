package com.vihu.ganlu.actions;

import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.TokenService;
import com.vihu.ganlu.service.PrivacyRequestService;
import com.vihu.ganlu.service.UserService;
import com.vihu.ganlu.configs.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class PrivacyRequestActionTests {
    private PrivacyRequestService service;
    private TokenService tokenService;
    private MockMvc mockMvc;
    private final Map<Integer, UserEntity> users = new HashMap<Integer, UserEntity>();

    @BeforeEach
    void setUp() {
        service = mock(PrivacyRequestService.class);
        UserService userService = mock(UserService.class);
        tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "secret", "test-token-secret-with-more-than-32-bytes");
        ReflectionTestUtils.setField(tokenService, "expirationSeconds", 3600L);
        users.put(1, user(1, 0));
        users.put(2, user(2, 1));
        users.put(3, user(3, 2));
        when(userService.findUserById(anyInt())).thenAnswer(invocation -> users.get(invocation.getArgument(0)));
        PrivacyRequestAction action = new PrivacyRequestAction(service);
        AuthInterceptor interceptor = new AuthInterceptor(tokenService, userService);
        mockMvc = standaloneSetup(action)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(interceptor)
                .build();
    }

    @Test
    void guestCannotCreatePrivacyTicket() throws Exception {
        mockMvc.perform(post("/privacy-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestType\":\"CORRECTION\",\"description\":\"更新资料\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is(401)));
        verify(service, never()).create(any(), any());
    }

    @Test
    void authenticatedUserCanCreateOwnTicketWithoutSubmittingUserId() throws Exception {
        when(service.create(any(), eq(users.get(3)))).thenReturn(44L);
        mockMvc.perform(post("/privacy-requests")
                        .header("Authorization", bearer(users.get(3)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestType\":\"CORRECTION\",\"description\":\"更新资料\",\"requesterUserId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content.ticketId", is(44)));
        verify(service).create(any(), eq(users.get(3)));
    }

    @Test
    void nonAdministratorCannotUseAdminQueue() throws Exception {
        mockMvc.perform(get("/admin/privacy-requests")
                        .header("Authorization", bearer(users.get(2))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
        verify(service, never()).findRecent(any(), anyInt(), anyInt(), any());
    }

    @Test
    void invalidResolutionReasonIsRejectedByValidation() throws Exception {
        mockMvc.perform(put("/admin/privacy-requests/10")
                        .header("Authorization", bearer(users.get(1)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\",\"decisionCode\":\"MANUAL_REVIEW\",\"decisionReason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)));
        verify(service, never()).process(anyLong(), any(), any());
    }

    private String bearer(UserEntity user) { return "Bearer " + tokenService.createToken(user); }

    private UserEntity user(int id, int level) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setLevel(level);
        user.setSessionVersion(0);
        return user;
    }
}
