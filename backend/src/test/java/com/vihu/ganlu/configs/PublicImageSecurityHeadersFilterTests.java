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
        assertEquals("image/png", response.getContentType());
        verify(chain).doFilter(request, response);
    }

    @Test
    void setsCanonicalContentTypesForEveryAllowedExtension() throws Exception {
        assertContentType("/images/team.jpg", "image/jpeg");
        assertContentType("/images/team.webp", "image/webp");
    }

    private void assertContentType(String uri, String expected) throws Exception {
        PublicImageSecurityHeadersFilter filter = new PublicImageSecurityHeadersFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertEquals(expected, response.getContentType());
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
    }
}
