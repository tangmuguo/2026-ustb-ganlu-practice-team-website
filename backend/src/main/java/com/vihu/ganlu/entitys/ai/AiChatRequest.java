package com.vihu.ganlu.entitys.ai;

import java.util.List;

/**
 * AI 对话请求 —— POST /ai/chat 入参。
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
}
