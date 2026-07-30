package com.vihu.ganlu.entitys.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * AI 对话请求 —— POST /ai/chat 入参。拒绝未知字段。
 */
@JsonIgnoreProperties(ignoreUnknown = false)
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
}
