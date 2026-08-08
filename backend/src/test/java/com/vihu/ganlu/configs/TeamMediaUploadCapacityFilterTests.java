package com.vihu.ganlu.configs;

import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.security.TokenService;
import com.vihu.ganlu.service.UserService;
import com.vihu.ganlu.service.impl.TeamMediaCapacityService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class TeamMediaUploadCapacityFilterTests {
    @Test
    void authenticatesAndReservesBeforeMultipartRequestContinuesThenReleases() throws Exception {
        Fixture fixture = fixture(1);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = mediaRequest(1024, true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        TeamMediaCapacityService.UploadAdmission admission =
                new TeamMediaCapacityService.UploadAdmission("r1", 7, 1024);
        when(fixture.capacity.reserveAdmission(7, 1024)).thenReturn(admission);

        fixture.filter.doFilter(request, response, chain);

        org.mockito.InOrder order = inOrder(fixture.token, fixture.users, fixture.capacity, chain);
        order.verify(fixture.token).verifyAndGetUserId("valid");
        order.verify(fixture.users).findUserById(7);
        order.verify(fixture.capacity).reserveAdmission(7, 1024);
        order.verify(fixture.capacity).bindToCurrentRequest(admission);
        order.verify(chain).doFilter(request, response);
        order.verify(fixture.capacity).clearCurrentRequest();
        order.verify(fixture.capacity).releaseAdmission("r1");
    }

    @Test
    void anonymousRequestIsRejectedBeforeCapacityOrBodyHandling() throws Exception {
        Fixture fixture = fixture(1);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = mediaRequest(2048, false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        fixture.filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verifyNoInteractions(fixture.capacity, fixture.token, fixture.users, chain);
    }

    @Test
    void studentRoleIsRejectedBeforeReservation() throws Exception {
        Fixture fixture = fixture(2);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        fixture.filter.doFilter(mediaRequest(2048, true), response, chain);

        assertEquals(403, response.getStatus());
        verifyNoInteractions(fixture.capacity, chain);
    }

    @Test
    void atomicReservationFailureStopsBeforeMultipartParsing() throws Exception {
        Fixture fixture = fixture(1);
        doThrow(new TeamMediaCapacityService.UploadAdmissionException(
                507, "Multipart 临时目录剩余空间不足"))
                .when(fixture.capacity).reserveAdmission(7, 2048);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        fixture.filter.doFilter(mediaRequest(2048, true), response, chain);

        assertEquals(507, response.getStatus());
        assertTrue(response.getContentAsString().contains("剩余空间不足"));
        verifyNoInteractions(chain);
        verify(fixture.capacity, never()).bindToCurrentRequest(any());
    }

    @Test
    void missingContentLengthIsRejectedAfterAuthenticationButBeforeReservation() throws Exception {
        Fixture fixture = fixture(1);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/team-content/media");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        fixture.filter.doFilter(request, response, chain);

        assertEquals(411, response.getStatus());
        verify(fixture.token).verifyAndGetUserId("valid");
        verifyNoInteractions(fixture.capacity, chain);
    }

    private Fixture fixture(int level) {
        TeamMediaCapacityService capacity = mock(TeamMediaCapacityService.class);
        TokenService token = mock(TokenService.class);
        UserService users = mock(UserService.class);
        UserEntity user = new UserEntity();
        user.setId(7);
        user.setLevel(level);
        when(token.verifyAndGetUserId("valid")).thenReturn(7);
        when(users.findUserById(7)).thenReturn(user);
        return new Fixture(capacity, token, users,
                new TeamMediaUploadCapacityFilter(capacity, token, users));
    }

    private MockHttpServletRequest mediaRequest(int length, boolean authenticated) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/team-content/media");
        if (authenticated) request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid");
        request.setContent(new byte[length]);
        return request;
    }

    private static final class Fixture {
        private final TeamMediaCapacityService capacity;
        private final TokenService token;
        private final UserService users;
        private final TeamMediaUploadCapacityFilter filter;

        private Fixture(TeamMediaCapacityService capacity, TokenService token,
                        UserService users, TeamMediaUploadCapacityFilter filter) {
            this.capacity = capacity;
            this.token = token;
            this.users = users;
            this.filter = filter;
        }
    }
}
