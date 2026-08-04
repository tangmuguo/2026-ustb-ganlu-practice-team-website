package com.vihu.ganlu.configs;

import com.vihu.ganlu.service.impl.TeamMediaCapacityService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class TeamMediaUploadCapacityFilterTests {
    @Test
    void checksBothDisksBeforeMultipartRequestContinues() throws Exception {
        TeamMediaCapacityService capacity = mock(TeamMediaCapacityService.class);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = mediaRequest(1024);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TeamMediaUploadCapacityFilter(capacity).doFilter(request, response, chain);

        verify(capacity).ensureCapacity(1024);
        verify(chain).doFilter(request, response);
    }

    @Test
    void insufficientTemporaryOrUploadSpaceStopsBeforeMultipartParsing() throws Exception {
        TeamMediaCapacityService capacity = mock(TeamMediaCapacityService.class);
        doThrow(new IllegalStateException("Multipart 临时目录剩余空间不足"))
                .when(capacity).ensureCapacity(2048);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = mediaRequest(2048);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TeamMediaUploadCapacityFilter(capacity).doFilter(request, response, chain);

        assertEquals(507, response.getStatus());
        assertTrue(response.getContentAsString().contains("剩余空间不足"));
        verifyNoInteractions(chain);
    }

    @Test
    void missingContentLengthIsRejectedBeforeBodyWrite() throws Exception {
        TeamMediaCapacityService capacity = mock(TeamMediaCapacityService.class);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/team-content/media");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TeamMediaUploadCapacityFilter(capacity).doFilter(request, response, chain);

        assertEquals(411, response.getStatus());
        verifyNoInteractions(capacity, chain);
    }

    private MockHttpServletRequest mediaRequest(long length) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/team-content/media");
        request.setContent(new byte[Math.toIntExact(length)]);
        return request;
    }
}
