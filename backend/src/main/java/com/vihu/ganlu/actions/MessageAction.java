package com.vihu.ganlu.actions;

import com.vihu.ganlu.entitys.DeleteReplyEntity;
import com.vihu.ganlu.entitys.MessageEntity;
import com.vihu.ganlu.entitys.ReplyEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.PublicEndpoint;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.service.impl.MessageServiceImpl;
import com.vihu.ganlu.utils.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/message")
public class MessageAction {
    private final MessageServiceImpl messageService;

    public MessageAction(MessageServiceImpl messageService) {
        this.messageService = messageService;
    }

    @RequireRoles({0, 1, 2})
    @PostMapping("/add")
    public ApiResponse<MessageEntity> addMessage(@RequestBody MessageEntity request, HttpServletRequest httpRequest) {
        MessageEntity result = messageService.addMessage(request.getContent(), currentUser(httpRequest).getId());
        return ApiResponse.success("留言发布成功", result);
    }

    @PublicEndpoint
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getMessages(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success("查询成功", messageService.getMessages(page, pageSize));
    }

    @RequireRoles({0, 1})
    @PostMapping("/deleteMessage")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            @RequestBody DeleteReplyEntity request,
            HttpServletRequest httpRequest) {
        boolean deleted = messageService.deleteMessage(request.getId(), currentUser(httpRequest).getId());
        return deleted
                ? ResponseEntity.ok(ApiResponse.success("留言已删除", null))
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "留言不存在"));
    }

    @RequireRoles({0, 1, 2})
    @PostMapping("/addReply")
    public ApiResponse<ReplyEntity> addReply(@RequestBody ReplyEntity request, HttpServletRequest httpRequest) {
        ReplyEntity result = messageService.addReply(
                request.getMessageId(), request.getContent(), currentUser(httpRequest).getId());
        return ApiResponse.success("回复发布成功", result);
    }

    @RequireRoles({0, 1})
    @PostMapping("/deleteReply")
    public ResponseEntity<ApiResponse<Void>> deleteReply(
            @RequestBody DeleteReplyEntity request,
            HttpServletRequest httpRequest) {
        boolean deleted = messageService.deleteReply(request.getId(), currentUser(httpRequest).getId());
        return deleted
                ? ResponseEntity.ok(ApiResponse.success("回复已删除", null))
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "回复不存在"));
    }

    private UserEntity currentUser(HttpServletRequest request) {
        return (UserEntity) request.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE);
    }
}
