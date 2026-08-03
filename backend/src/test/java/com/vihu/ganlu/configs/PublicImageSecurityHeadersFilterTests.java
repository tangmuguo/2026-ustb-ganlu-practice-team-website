package com.vihu.ganlu.configs;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PublicImageSecurityHeadersFilterTests {
    @Test
    void addsNoSniffHeadersToPublicImages() throws Exception {
        PublicImageSecurityHeadersFilter filter = new PublicImageSecurityHeadersFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/images/team.png");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("default-src 'none'; sandbox", response.getHeader("Content-Security-Policy"));
        verify(chain).doFilter(request, response);
    }
}
