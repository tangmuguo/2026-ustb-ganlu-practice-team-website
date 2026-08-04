package com.vihu.ganlu.actions;

import com.google.common.collect.ImmutableMap;
import com.vihu.ganlu.entitys.MessageEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.message.DeleteContentRequest;
import com.vihu.ganlu.entitys.message.MessageCreateRequest;
import com.vihu.ganlu.entitys.message.ReplyCreateRequest;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.PublicEndpoint;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.service.impl.MessageServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;
import java.util.Map;

@RestController
@RequestMapping("/message")
public class MessageAction {
    @Autowired
    private MessageServiceImpl messageService;

    // 添加留言
    @RequireRoles({0, 1, 2})
    @PostMapping("/add")
    public ResponseEntity<?> addMessage(@RequestBody MessageCreateRequest message, HttpServletRequest request) {
        try {
            MessageEntity created = messageService.addMessage(message, currentUser(request).getId());
            return ok("添加成功", ImmutableMap.of("id", created.getId()));
        } catch (RuntimeException ex) {
            return error(ex);
        }
    }

    // 获取留言列表
    @PublicEndpoint
    @GetMapping("/list")
    public ResponseEntity<?> getMessages(@RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "10") int pageSize) {
        try {
            Map<String, Object> maps = messageService.getMessages(page, pageSize);
            return ok("查询成功", maps);
        } catch (RuntimeException ex) {
            return error(ex);
        }
    }

    // 删除留言（管理员）
    @RequireRoles({0, 1})
    @PostMapping("/deleteMessage")
    public ResponseEntity<?> deleteMessage(@RequestBody DeleteContentRequest deleteRequest,
                                           HttpServletRequest request) {
        try {
            messageService.deleteMessage(deleteRequest.getId(), currentUser(request).getId());
            return ok("删除成功", null);
        } catch (RuntimeException ex) {
            return error(ex);
        }
    }

    // 添加回复
    @RequireRoles({0, 1, 2})
    @PostMapping("/addReply")
    public ResponseEntity<?> addReply(@RequestBody ReplyCreateRequest reply, HttpServletRequest request) {
        try {
            Integer replyId = messageService.addReply(reply, currentUser(request).getId()).getId();
            return ok("添加成功", ImmutableMap.of("id", replyId));
        } catch (RuntimeException ex) {
            return error(ex);
        }
    }

    // 删除回复（管理员）
    @RequireRoles({0, 1})
    @PostMapping("/deleteReply")
    public ResponseEntity<?> deleteReply(@RequestBody DeleteContentRequest deleteRequest,
                                         HttpServletRequest request) {
        try {
            messageService.deleteReply(deleteRequest.getId(), currentUser(request).getId());
            return ok("删除成功", null);
        } catch (RuntimeException ex) {
            return error(ex);
        }
    }

    private UserEntity currentUser(HttpServletRequest request) {
        return (UserEntity) request.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE);
    }

    private ResponseEntity<?> ok(String message, Object content) {
        return ResponseEntity.ok(ImmutableMap.of(
                "code", HttpStatus.OK.value(),
                "message", message,
                "content", content == null ? ImmutableMap.of() : content
        ));
    }

    private ResponseEntity<?> error(RuntimeException ex) {
        HttpStatus status;
        if (ex instanceof IllegalArgumentException) {
            status = HttpStatus.BAD_REQUEST;
        } else if (ex instanceof NoSuchElementException) {
            status = HttpStatus.NOT_FOUND;
        } else if (ex instanceof SecurityException) {
            status = HttpStatus.FORBIDDEN;
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status).body(ImmutableMap.of(
                "code", status.value(),
                "message", ex.getMessage(),
                "content", ImmutableMap.of()
        ));
    }
}
