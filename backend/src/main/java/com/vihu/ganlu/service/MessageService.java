package com.vihu.ganlu.service;

import java.util.Map;

public interface MessageService {
    Map<String, Object> getMessageList(int page, int pageSize);

    void addMessage(String content);

    void addReply(Integer messageId, String content);

    void deleteMessage(Integer id);

    void deleteReply(Integer id);
}