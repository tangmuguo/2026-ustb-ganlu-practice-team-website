package com.vihu.ganlu.entitys.ai;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * AI 对话请求 —— POST /ai/chat 入参。拒绝未知字段。
 */
public class AiChatRequest {

    private List<AiMessageDto> messages;

    public AiChatRequest() {
    }

    public List<AiMessageDto> getMessages() {
        return messages;
    }

    public void setMessages(List<AiMessageDto> messages) {
        this.messages = messages;
    }

    /** 拒绝请求体顶层未知字段，返回可理解的 400 错误 */
    @JsonAnySetter
    public void rejectUnknown(String field, Object value) {
        throw new IllegalArgumentException("未知字段: " + field);
    }
}
