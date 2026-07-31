package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.UserEntity;
import java.util.Map;

public interface MessageService {

    Map<String, Object> getMessages(int page, int pageSize);

    int addMessage(String content, Integer loginUserId);

    int addReply(Integer messageId, String content, Integer loginUserId);

    int deleteMessage(Integer messageId, UserEntity loginUser);

    int deleteReply(Integer replyId, UserEntity loginUser);
}