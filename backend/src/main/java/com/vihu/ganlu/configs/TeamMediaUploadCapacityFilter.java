package com.vihu.ganlu.configs;

import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.TokenService;
import com.vihu.ganlu.service.UserService;
import com.vihu.ganlu.service.impl.TeamMediaCapacityService;
import com.vihu.ganlu.utils.FileStorageUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TeamMediaUploadCapacityFilter extends OncePerRequestFilter {
    private final TeamMediaCapacityService capacityService;
    private final TokenService tokenService;
    private final UserService userService;

    public TeamMediaUploadCapacityFilter(
            TeamMediaCapacityService capacityService,
            TokenService tokenService,
            UserService userService) {
        this.capacityService = capacityService;
        this.tokenService = tokenService;
        this.userService = userService;
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
        UserEntity currentUser = authenticateBeforeBody(request, response);
        if (currentUser == null) return;

        long contentLength = request.getContentLengthLong();
        if (contentLength <= 0) {
            writeError(response, HttpServletResponse.SC_LENGTH_REQUIRED,
                    "附件上传必须提供 Content-Length，以便在写入临时文件前预留磁盘空间");
            return;
        }
        if (contentLength > FileStorageUtil.MAX_VIDEO_SIZE + 10L * 1024L * 1024L) {
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "附件请求大小超过限制");
            return;
        }

        TeamMediaCapacityService.UploadAdmission admission;
        try {
            admission = capacityService.reserveAdmission(currentUser.getId(), contentLength);
        } catch (TeamMediaCapacityService.UploadAdmissionException error) {
            writeError(response, error.getHttpStatus(), error.getMessage());
            return;
        } catch (RuntimeException error) {
            log.error("创建附件上传容量预留失败", error);
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "上传服务暂时不可用，请稍后重试");
            return;
        }

        capacityService.bindToCurrentRequest(admission);
        try {
            filterChain.doFilter(request, response);
        } finally {
            capacityService.clearCurrentRequest();
            try {
                capacityService.releaseAdmission(admission.getReservationId());
            } catch (RuntimeException error) {
                // ACTIVE 记录带过期时间；数据库短暂故障不会造成永久额度泄漏。
                log.error("释放附件在途容量失败，等待过期回收: reservationId={}",
                        admission.getReservationId(), error);
            }
        }
    }

    private UserEntity authenticateBeforeBody(
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "请先登录");
            return null;
        }
        try {
            String token = authorization.substring("Bearer ".length()).trim();
            if (token.isEmpty()) throw new IllegalArgumentException("empty token");
            Integer userId = tokenService.verifyAndGetUserId(token);
            UserEntity user = userService.findUserById(userId);
            if (user == null || user.getId() == null || user.getLevel() == null) {
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "账号不存在或已失效");
                return null;
            }
            if (user.getLevel() != 0 && user.getLevel() != 1) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "无附件上传权限");
                return null;
            }
            request.setAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE, user);
            return user;
        } catch (RuntimeException error) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Token无效或已过期");
            return null;
        }
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String safe = message == null ? "附件上传被拒绝" : message
                .replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", " ").replace("\n", " ");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + safe + "\"}");
    }
}
