package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.ai.AiChatRequest;
import com.vihu.ganlu.entitys.ai.AiChatResponse;

/**
 * AI 小助手服务接口。
 */
public interface AiService {

    /**
     * 发送多轮对话消息到 DeepSeek，返回最终回答。
     *
     * @param request 前端提交的 messages 数组
     * @param userId  当前登录用户 ID，用于频率限制（可为 null，跳过限制）
     * @return AI 回答，不包含 reasoning_content
     */
    AiChatResponse chat(AiChatRequest request, Integer userId);
}
