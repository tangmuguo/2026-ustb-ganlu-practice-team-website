package com.vihu.ganlu.configs;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Baseline headers for responses served by Spring; Nginx must mirror them for the SPA. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SecurityResponseHeadersFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()");
        if (!isPublicImageRequest(request)) {
            response.setHeader("Content-Security-Policy",
                    "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; "
                            + "img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; script-src 'self'; "
                            + "connect-src 'self'; font-src 'self' data:");
        }
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        filterChain.doFilter(request, response);
    }

    private boolean isPublicImageRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path.startsWith("/images/");
    }
}
