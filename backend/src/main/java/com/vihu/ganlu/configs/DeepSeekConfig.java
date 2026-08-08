package com.vihu.ganlu.configs;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * AI 模块 HTTP 客户端配置 —— 使用 RestTemplateBuilder 设置超时。
 */
@Configuration
public class DeepSeekConfig {

    private final DeepSeekProperties properties;

    public DeepSeekConfig(DeepSeekProperties properties) {
        this.properties = properties;
    }

    @Bean
    public RestTemplate aiRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeout()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeout()))
                .build();
    }
}
