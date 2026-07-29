package com.vihu.ganlu.security;

import com.vihu.ganlu.actions.BannerAction;
import com.vihu.ganlu.entitys.BannerEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthInterceptorTests {
    private TokenService tokenService;
    private UserService userService;
    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "secret", "test-token-secret-with-more-than-32-bytes");
        ReflectionTestUtils.setField(tokenService, "expirationSeconds", 3600L);
        userService = mock(UserService.class);
        interceptor = new AuthInterceptor(tokenService, userService);
    }

    @Test
    void allowsExplicitPublicEndpointWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/banner/list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, bannerHandler("getBannerList")));
    }

    @Test
    void rejectsProtectedEndpointWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/banner/add");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response,
                bannerHandler("addBanner", BannerEntity.class)));
        assertEquals(401, response.getStatus());
    }

    @Test
    void rejectsTeamAccountFromAdministratorOnlyEndpoint() throws Exception {
        UserEntity team = user(7, 1);
        when(userService.findUserById(7)).thenReturn(team);
        MockHttpServletRequest request = authorizedRequest(team, "/banner/add");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response,
                bannerHandler("addBanner", BannerEntity.class)));
        assertEquals(403, response.getStatus());
    }

    @Test
    void allowsAdministratorAndUsesCurrentDatabaseRole() throws Exception {
        UserEntity administrator = user(1, 0);
        when(userService.findUserById(1)).thenReturn(administrator);
        MockHttpServletRequest request = authorizedRequest(administrator, "/banner/add");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response,
                bannerHandler("addBanner", BannerEntity.class)));
        assertSame(administrator, request.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE));
    }

    private MockHttpServletRequest authorizedRequest(UserEntity user, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader("Authorization", "Bearer " + tokenService.createToken(user));
        return request;
    }

    private HandlerMethod bannerHandler(String methodName, Class<?>... parameterTypes) throws Exception {
        return new HandlerMethod(new BannerAction(),
                BannerAction.class.getMethod(methodName, parameterTypes));
    }

    private UserEntity user(int id, int level) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setLevel(level);
        return user;
    }
}
