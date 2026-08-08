package com.vihu.ganlu.entitys.ai;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * AI 对话消息 DTO —— 前端只允许 user / assistant 两种角色。拒绝未知字段。
 */
public class AiMessageDto {
    private String role;
    private String content;

    public AiMessageDto() {
    }

    public AiMessageDto(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    /** 拒绝消息元素中的未知字段，返回可理解的 400 错误 */
    @JsonAnySetter
    public void rejectUnknown(String field, Object value) {
        throw new IllegalArgumentException("未知字段: " + field);
    }
}
