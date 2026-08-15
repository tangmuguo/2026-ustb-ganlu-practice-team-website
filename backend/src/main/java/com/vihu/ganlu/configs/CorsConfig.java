package com.vihu.ganlu.configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import javax.annotation.PostConstruct;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${app.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    private final Environment environment;

    public CorsConfig(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateOrigins() {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toArray(String[]::new);
        if (origins.length == 0 || Arrays.stream(origins).anyMatch(value -> "*".equals(value))) {
            throw new IllegalStateException("app.allowed-origins 必须是明确来源，不能使用通配符");
        }
        if (isProductionProfileActive()
                && Arrays.stream(origins).anyMatch(value -> !value.startsWith("https://"))) {
            throw new IllegalStateException("生产环境 app.allowed-origins 只能使用 HTTPS 来源");
        }
    }

    /**
     * Spring only exposes default profiles when no explicit active profile was
     * supplied.  The application deliberately defaults to {@code prod}, so an
     * empty active-profile array must not silently disable production CORS
     * validation.
     */
    private boolean isProductionProfileActive() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles != null && activeProfiles.length > 0) {
            return Arrays.asList(activeProfiles).contains("prod");
        }
        return Arrays.asList(environment.getDefaultProfiles()).contains("prod");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
        registry.addMapping("/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept", "X-Requested-With")
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String normalizedPath = uploadDir.endsWith("/") || uploadDir.endsWith("\\")
                ? uploadDir
                : uploadDir + "/";
        // 仅公开图片目录。课件原文件与预览必须通过鉴权接口读取。
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + normalizedPath + "images/")
                .setCachePeriod(3600);
    }
}
