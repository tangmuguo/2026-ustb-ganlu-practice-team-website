package com.vihu.ganlu.entitys.ai;

/**
 * AI 对话响应 —— 只向前端返回最终答案和 requestId。
 */
public class AiChatResponse {

    private String answer;
    private String requestId;

    public AiChatResponse() {
    }

    public AiChatResponse(String answer, String requestId) {
        this.answer = answer;
        this.requestId = requestId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
