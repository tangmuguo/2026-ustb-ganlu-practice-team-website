package com.vihu.ganlu.configs;

import com.vihu.ganlu.service.impl.TeamMediaCapacityService;
import com.vihu.ganlu.utils.FileStorageUtil;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TeamMediaUploadCapacityFilter extends OncePerRequestFilter {
    private final TeamMediaCapacityService capacityService;

    public TeamMediaUploadCapacityFilter(TeamMediaCapacityService capacityService) {
        this.capacityService = capacityService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String path = context != null && !context.isEmpty() && uri.startsWith(context)
                ? uri.substring(context.length()) : uri;
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !("/team-content/media".equals(path) || "/team-content/media/".equals(path));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength <= 0) {
            writeError(response, HttpServletResponse.SC_LENGTH_REQUIRED,
                    "附件上传必须提供 Content-Length，以便在写入临时文件前检查磁盘空间");
            return;
        }
        if (contentLength > FileStorageUtil.MAX_VIDEO_SIZE + 10L * 1024L * 1024L) {
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "附件请求大小超过限制");
            return;
        }
        try {
            capacityService.ensureCapacity(contentLength);
            filterChain.doFilter(request, response);
        } catch (RuntimeException error) {
            writeError(response, 507, error.getMessage());
        }
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String safe = message == null ? "附件上传被拒绝" : message.replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + safe + "\"}");
    }
}
