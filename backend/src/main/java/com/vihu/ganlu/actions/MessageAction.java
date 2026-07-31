package com.vihu.ganlu.actions;

import com.google.common.collect.ImmutableMap;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.message.DeleteContentRequest;
import com.vihu.ganlu.entitys.message.MessageCreateRequest;
import com.vihu.ganlu.entitys.message.ReplyCreateRequest;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.PublicEndpoint;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/message")
@Validated
public class MessageAction {

    @Autowired
    private MessageService messageService;

    /**
     * 分页查询留言列表
     * 权限：游客开放
     */
    @GetMapping("/list")
    @PublicEndpoint
    public ResponseEntity<Map<String, Object>> getMessages(@RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "10") int pageSize) {
        // 分页参数边界校验
        if (page < 1) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ImmutableMap.of(
                    "code", 400,
                    "message", "page 参数必须大于等于 1",
                    "content", null
            ));
        }
        if (pageSize < 1 || pageSize > 50) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ImmutableMap.of(
                    "code", 400,
                    "message", "pageSize 范围为 1~50",
                    "content", null
            ));
        }

        Map<String, Object> data = messageService.getMessages(page, pageSize);
        return ResponseEntity.ok(ImmutableMap.of(
                "code", 200,
                "message", "查询成功",
                "content", data
        ));
    }

    /**
     * 新增留言
     * 权限：登录用户（level 0/1/2 均可）
     */
    @PostMapping("/add")
    @RequireRoles({0, 1, 2})
    public ResponseEntity<Map<String, Object>> addMessage(@Valid @RequestBody MessageCreateRequest request,
                                                          HttpServletRequest httpRequest) {
        UserEntity loginUser = currentUser(httpRequest);
        int affect = messageService.addMessage(request.getContent().trim(), loginUser.getId());

        if (affect > 0) {
            return ResponseEntity.ok(ImmutableMap.of(
                    "code", 200,
                    "message", "留言添加成功",
                    "content", null
            ));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ImmutableMap.of(
                "code", 400,
                "message", "留言内容不合法",
                "content", null
        ));
    }

    /**
     * 新增回复
     * 权限：登录用户（level 0/1/2 均可）
     */
    @PostMapping("/addReply")
    @RequireRoles({0, 1, 2})
    public ResponseEntity<Map<String, Object>> addReply(@Valid @RequestBody ReplyCreateRequest request,
                                                         HttpServletRequest httpRequest) {
        UserEntity loginUser = currentUser(httpRequest);
        int affect = messageService.addReply(request.getMessageId(), request.getContent().trim(), loginUser.getId());

        if (affect > 0) {
            return ResponseEntity.ok(ImmutableMap.of(
                    "code", 200,
                    "message", "回复添加成功",
                    "content", null
            ));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ImmutableMap.of(
                "code", 404,
                "message", "留言不存在或已删除",
                "content", null
        ));
    }

    /**
     * 删除留言
     * 权限：仅 level 0/1 管理员
     */
    @PostMapping("/delete")
    @RequireRoles({0, 1})
    public ResponseEntity<Map<String, Object>> deleteMessage(@Valid @RequestBody DeleteContentRequest request,
                                                             HttpServletRequest httpRequest) {
        UserEntity loginUser = currentUser(httpRequest);
        int affect = messageService.deleteMessage(request.getId(), loginUser);

        if (affect > 0) {
            return ResponseEntity.ok(ImmutableMap.of(
                    "code", 200,
                    "message", "留言删除成功",
                    "content", null
            ));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ImmutableMap.of(
                "code", 404,
                "message", "留言不存在或已删除",
                "content", null
        ));
    }

    /**
     * 删除回复
     * 权限：仅 level 0/1 管理员
     */
    @PostMapping("/deleteReply")
    @RequireRoles({0, 1})
    public ResponseEntity<Map<String, Object>> deleteReply(@Valid @RequestBody DeleteContentRequest request,
                                                           HttpServletRequest httpRequest) {
        UserEntity loginUser = currentUser(httpRequest);
        int affect = messageService.deleteReply(request.getId(), loginUser);

        if (affect > 0) {
            return ResponseEntity.ok(ImmutableMap.of(
                    "code", 200,
                    "message", "回复删除成功",
                    "content", null
            ));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ImmutableMap.of(
                "code", 404,
                "message", "回复不存在或已删除",
                "content", null
        ));
    }

    /**
     * 从请求中取出拦截器存入的当前登录用户
     */
    private UserEntity currentUser(HttpServletRequest request) {
        return (UserEntity) request.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE);
    }
}