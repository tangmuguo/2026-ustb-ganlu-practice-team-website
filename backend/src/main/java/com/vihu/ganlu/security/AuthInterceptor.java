package com.vihu.ganlu.security;

import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.service.UserService;
import org.springframework.core.annotation.AnnotatedElementUtils;
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

    public AuthInterceptor(TokenService tokenService, UserService userService) {
        this.tokenService = tokenService;
        this.userService = userService;
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

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return reject(response, HttpStatus.UNAUTHORIZED, "请先登录");
        }

        UserEntity currentUser;
        try {
            String token = authorization.substring("Bearer ".length()).trim();
            if (token.isEmpty()) {
                return reject(response, HttpStatus.UNAUTHORIZED, "Token无效或已过期");
            }
            Integer userId = tokenService.verifyAndGetUserId(token);
            currentUser = userService.findUserById(userId);
        } catch (RuntimeException ex) {
            return reject(response, HttpStatus.UNAUTHORIZED, "Token无效或已过期");
        }

        if (currentUser == null || currentUser.getLevel() == null) {
            return reject(response, HttpStatus.UNAUTHORIZED, "账号不存在或已失效");
        }

        RequireRoles requireRoles = findAnnotation(handlerMethod, RequireRoles.class);
        if (requireRoles != null && Arrays.stream(requireRoles.value())
                .noneMatch(level -> level == currentUser.getLevel())) {
            return reject(response, HttpStatus.FORBIDDEN, "无访问权限");
        }

        request.setAttribute(CURRENT_USER_ATTRIBUTE, currentUser);
        return true;
    }

    private boolean reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status.value() + ",\"message\":\"" + message + "\"}");
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
