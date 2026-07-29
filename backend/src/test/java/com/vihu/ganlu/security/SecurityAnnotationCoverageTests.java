package com.vihu.ganlu.security;

import com.vihu.ganlu.actions.BannerAction;
import com.vihu.ganlu.actions.CourseDetailAction;
import com.vihu.ganlu.actions.FengCaiAction;
import com.vihu.ganlu.actions.MessageAction;
import com.vihu.ganlu.actions.NewsAction;
import com.vihu.ganlu.actions.UserAction;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityAnnotationCoverageTests {
    private static final List<Class<?>> ACTIONS = Arrays.asList(
            UserAction.class,
            BannerAction.class,
            NewsAction.class,
            CourseDetailAction.class,
            FengCaiAction.class,
            MessageAction.class
    );

    @Test
    void everyApiMethodExplicitlyDeclaresItsSecurityPolicy() {
        List<String> missingPolicies = new ArrayList<>();

        for (Class<?> action : ACTIONS) {
            for (Method method : action.getDeclaredMethods()) {
                if (isEndpoint(method)
                        && !method.isAnnotationPresent(PublicEndpoint.class)
                        && !method.isAnnotationPresent(RequireRoles.class)) {
                    missingPolicies.add(action.getSimpleName() + "." + method.getName());
                }
            }
        }

        assertTrue(missingPolicies.isEmpty(),
                "接口必须显式声明 @PublicEndpoint 或 @RequireRoles: " + missingPolicies);
    }

    private boolean isEndpoint(Method method) {
        return method.isAnnotationPresent(RequestMapping.class)
                || method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class)
                || method.isAnnotationPresent(PatchMapping.class);
    }
}
