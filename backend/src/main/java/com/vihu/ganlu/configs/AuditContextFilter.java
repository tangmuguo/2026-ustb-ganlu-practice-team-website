package com.vihu.ganlu.configs;

import com.vihu.ganlu.audit.AuditRequestContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Generates server-controlled request IDs and accepts XFF only from explicit trusted proxies. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditContextFilter extends OncePerRequestFilter {
    private final Set<String> trustedProxyAddresses;

    public AuditContextFilter(@Value("${audit.trusted-proxy-addresses:127.0.0.1,::1}") String trustedProxyAddresses) {
        this.trustedProxyAddresses = new HashSet<String>();
        Arrays.stream(trustedProxyAddresses.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(this.trustedProxyAddresses::add);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        String sourceIp = sourceIp(request);
        AuditRequestContext.set(requestId, sourceIp, limit(request.getMethod(), 12),
                limit(request.getRequestURI(), 512), limit(request.getServerName(), 255),
                request.getServerPort(), limit(request.getHeader("User-Agent"), 512));
        response.setHeader("X-Request-Id", requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            AuditRequestContext.clear();
        }
    }

    private String sourceIp(HttpServletRequest request) {
        String remoteAddress = limit(request.getRemoteAddr(), 64);
        if (!trustedProxyAddresses.contains(remoteAddress)) return remoteAddress;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null) return remoteAddress;
        String first = forwarded.split(",", 2)[0].trim();
        return first.matches("[0-9a-fA-F:.]{1,64}") ? first : remoteAddress;
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        String normalized = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
