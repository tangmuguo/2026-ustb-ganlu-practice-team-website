package com.vihu.ganlu.configs;

import com.vihu.ganlu.audit.AuditRequestContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small in-process safety valve for login, publishing and report submission.
 * A production multi-node deployment must also enforce equivalent limits at
 * the trusted reverse proxy; this filter intentionally never stores bodies,
 * credentials or Authorization values.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
@ConditionalOnProperty(name = "rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class SensitiveEndpointRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MILLIS = 60_000L;
    private final Map<String, Window> windows = new ConcurrentHashMap<String, Window>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI();
        return !"/user/login".equals(path)
                && !"/message/add".equals(path)
                && !"/message/addReply".equals(path)
                && !"/reports".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        int limit = "/user/login".equals(path) ? 10 : 20;
        AuditRequestContext.Values context = AuditRequestContext.get();
        String source = context == null || context.getSourceIp() == null ? request.getRemoteAddr() : context.getSourceIp();
        String key = path + '|' + (source == null ? "unknown" : source);
        if (!allow(key, limit)) {
            response.setStatus(429);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json;charset=UTF-8");
            String requestId = context == null || context.getRequestId() == null ? "" : context.getRequestId();
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"content\":null,\"requestId\":\""
                    + requestId + "\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean allow(String key, int limit) {
        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(key, ignored -> new Window(now));
        synchronized (window) {
            if (now - window.startedAt >= WINDOW_MILLIS) {
                window.startedAt = now;
                window.count = 0;
            }
            window.count++;
            if (windows.size() > 10_000) {
                windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt > WINDOW_MILLIS * 2);
            }
            return window.count <= limit;
        }
    }

    private static final class Window {
        private long startedAt;
        private int count;

        private Window(long startedAt) { this.startedAt = startedAt; }
    }
}
