package com.vihu.ganlu.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek / AI 供应商配置 —— 从 application.properties 读取，不支持硬编码 Key。
 * 缺少 Key 时应用可以启动，但调用 AI 接口返回 "AI 服务未配置"。
 */
@Component
@ConfigurationProperties(prefix = "ai")
public class DeepSeekProperties {

    /** 是否启用 AI 功能，默认 false */
    private boolean enabled = false;

    /** DeepSeek API 基础地址，如 https://api.deepseek.com */
    private String baseUrl = "";

    /** API Key，应从环境变量 DEEPSEEK_API_KEY 注入 */
    private String apiKey = "";

    /** 模型名，默认 deepseek-v4-flash */
    private String model = "deepseek-v4-flash";

    /** 连接超时（毫秒），默认 10 秒 */
    private int connectTimeout = 10000;

    /** 读取超时（毫秒），默认 60 秒 */
    private int readTimeout = 60000;

    // ---- getters / setters ----

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    /**
     * 判断 AI 是否已正确配置（启用 + 有 baseUrl + 有 apiKey）。
     */
    public boolean isConfigured() {
        return enabled
                && baseUrl != null && !baseUrl.trim().isEmpty()
                && apiKey != null && !apiKey.trim().isEmpty();
    }

    /**
     * 解析完整的 chat/completions 端点地址（不插入 /v1）。
     */
    public String resolveEndpoint() {
        String normalized = baseUrl.trim();
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        if (normalized.endsWith("/")) {
            return normalized + "chat/completions";
        }
        return normalized + "/chat/completions";
    }
}
