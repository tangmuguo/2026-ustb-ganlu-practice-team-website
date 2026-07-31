package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.UserEntity;
import java.util.Map;

public interface MessageService {

    Map<String, Object> getMessageList(int page, int pageSize);

    void addMessage(String content, Integer loginUserId);

    void addReply(Integer messageId, String content, Integer loginUserId);

    void deleteMessage(Integer messageId, UserEntity loginUser);

    void deleteReply(Integer replyId, UserEntity loginUser);
}