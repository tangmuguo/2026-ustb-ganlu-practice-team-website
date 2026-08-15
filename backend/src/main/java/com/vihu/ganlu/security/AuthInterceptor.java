package com.vihu.ganlu.security;

import com.vihu.ganlu.audit.AuditRequestContext;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.service.AuditEventService;
import com.vihu.ganlu.service.UserService;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    public static final String CURRENT_USER_ATTRIBUTE = AuthInterceptor.class.getName() + ".currentUser";
    private static final String ACTION_PACKAGE = "com.vihu.ganlu.actions";

    private final TokenService tokenService;
    private final UserService userService;
    private final AuthContext authContext;
    private final AuditEventService auditEventService;

    @Autowired
    public AuthInterceptor(TokenService tokenService, UserService userService, AuthContext authContext,
                           AuditEventService auditEventService) {
        this.tokenService = tokenService;
        this.userService = userService;
        this.authContext = authContext;
        this.auditEventService = auditEventService;
    }

    public AuthInterceptor(TokenService tokenService, UserService userService, AuthContext authContext) {
        this(tokenService, userService, authContext, null);
    }

    public AuthInterceptor(TokenService tokenService, UserService userService) {
        this(tokenService, userService, new AuthContext(), null);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Package handlerPackage = handlerMethod.getBeanType().getPackage();
        if (handlerPackage == null || !handlerPackage.getName().startsWith(ACTION_PACKAGE)) {
            return true;
        }

        if (hasAnnotation(handlerMethod, PublicEndpoint.class)) {
            return true;
        }

        UserEntity currentUser = request.getAttribute(CURRENT_USER_ATTRIBUTE) instanceof UserEntity
                ? (UserEntity) request.getAttribute(CURRENT_USER_ATTRIBUTE) : null;
        if (currentUser == null) {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return reject(request, response, HttpStatus.UNAUTHORIZED, "请先登录", null, "MISSING_TOKEN");
            }
            try {
                String token = authorization.substring("Bearer ".length()).trim();
                if (token.isEmpty()) {
                    return reject(request, response, HttpStatus.UNAUTHORIZED, "Token无效或已过期", null, "INVALID_TOKEN");
                }
                Integer userId = tokenService.verifyAndGetUserId(token);
                currentUser = userService.findUserById(userId);
                if (!tokenService.isTokenCurrent(token, currentUser)) {
                    return reject(request, response, HttpStatus.UNAUTHORIZED, "Token无效或已过期", null, "STALE_TOKEN");
                }
            } catch (RuntimeException ex) {
                return reject(request, response, HttpStatus.UNAUTHORIZED, "Token无效或已过期", null, "INVALID_TOKEN");
            }
        }

        if (currentUser == null || currentUser.getLevel() == null) {
            return reject(request, response, HttpStatus.UNAUTHORIZED, "账号不存在或已失效", null, "ACCOUNT_INACTIVE");
        }

        RequireRoles requireRoles = findAnnotation(handlerMethod, RequireRoles.class);
        int currentLevel = currentUser.getLevel();
        if (requireRoles != null && Arrays.stream(requireRoles.value())
                .noneMatch(level -> level == currentLevel)) {
            return reject(request, response, HttpStatus.FORBIDDEN, "无访问权限", currentUser, "ROLE_NOT_ALLOWED");
        }

        request.setAttribute(CURRENT_USER_ATTRIBUTE, currentUser);
        authContext.setCurrentUser(currentUser);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        authContext.clear();
    }

    private boolean reject(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message,
                           UserEntity actor, String reasonCode) throws IOException {
        if (auditEventService != null) {
            auditEventService.record(actor, "AUTHORIZATION", "HTTP_ENDPOINT", request.getRequestURI(), "DENIED", reasonCode);
        }
        AuditRequestContext.Values context = AuditRequestContext.get();
        String requestId = context == null ? null : context.getRequestId();
        response.setStatus(status.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getWriter().write("{\"code\":" + status.value()
                + ",\"message\":\"" + message + "\",\"content\":null,\"requestId\":"
                + (requestId == null ? "null" : "\"" + requestId + "\"") + "}");
        return false;
    }

    private <A extends java.lang.annotation.Annotation> boolean hasAnnotation(
            HandlerMethod handlerMethod, Class<A> annotationType) {
        return findAnnotation(handlerMethod, annotationType) != null;
    }

    private <A extends java.lang.annotation.Annotation> A findAnnotation(
            HandlerMethod handlerMethod, Class<A> annotationType) {
        A annotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), annotationType);
        return annotation != null
                ? annotation
                : AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), annotationType);
    }
}
