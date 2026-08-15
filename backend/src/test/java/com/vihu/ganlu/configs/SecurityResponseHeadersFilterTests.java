package com.vihu.ganlu.configs;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SecurityResponseHeadersFilterTests {
    @Test
    void addsBaselineHeadersAndCspToApplicationResponses() throws Exception {
        SecurityResponseHeadersFilter filter = new SecurityResponseHeadersFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/message/list");
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertEquals("strict-origin-when-cross-origin", response.getHeader("Referrer-Policy"));
        assertEquals("max-age=31536000; includeSubDomains", response.getHeader("Strict-Transport-Security"));
        assertEquals("default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; "
                        + "img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; script-src 'self'; "
                        + "connect-src 'self'; font-src 'self' data:",
                response.getHeader("Content-Security-Policy"));
        verify(chain).doFilter(request, response);
    }

    @Test
    void leavesTheStricterPublicImageCspToTheImageFilter() throws Exception {
        SecurityResponseHeadersFilter filter = new SecurityResponseHeadersFilter();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/images/team.png"), response, mock(FilterChain.class));

        assertNull(response.getHeader("Content-Security-Policy"));
    }
}
