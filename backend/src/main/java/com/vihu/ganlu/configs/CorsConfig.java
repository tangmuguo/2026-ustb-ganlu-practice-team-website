package com.vihu.ganlu.configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.HttpResource;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 允许所有路径
                .allowedOrigins("http://47.95.209.65",
                        "https://47.95.209.65",
                        "http://localhost:5173"
                        ) // 允许所有来源（生产环境建议指定域名）
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 允许的 HTTP 方法
                .allowedHeaders("*") // 允许所有请求头
                .allowCredentials(true) // 是否允许携带 Cookie（默认 false）
                .maxAge(3600); // 预检请求缓存时间（秒）
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 统一处理路径格式（兼容Windows/Linux）
        String normalizedPath = uploadDir.endsWith("/") ? uploadDir : uploadDir + "/";

        // 映射 /images/** 到文件系统的 images 目录
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + normalizedPath + "images/")
                .setCachePeriod(3600);


        // 映射 /materials/** 到文件系统的 materials 目录
        registry.addResourceHandler("/materials/**")
                .addResourceLocations("file:" + normalizedPath + "materials/")
                .setCachePeriod(3600);
    }
}
