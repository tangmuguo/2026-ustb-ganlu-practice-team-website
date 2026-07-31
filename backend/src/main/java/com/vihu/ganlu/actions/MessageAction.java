package com.vihu.ganlu.actions;

import com.google.common.collect.ImmutableMap;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.message.DeleteContentRequest;
import com.vihu.ganlu.entitys.message.MessageCreateRequest;
import com.vihu.ganlu.entitys.message.ReplyCreateRequest;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.PublicEndpoint;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.service.impl.MessageServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
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
    private MessageServiceImpl messageService;

    // 添加留言
    @RequireRoles({0, 1, 2})
    @PostMapping("/add")
    public ResponseEntity<?> addMessage(@Valid @RequestBody MessageCreateRequest request, HttpServletRequest httpRequest) {
        UserEntity loginUser = currentUser(httpRequest);
        Integer userId = loginUser.getId();
        String content = request.getContent().trim();
        int affect = messageService.addMessage(content, userId);
        if (affect > 0) {
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "success", true,
                    "message", "留言添加成功"
            ));
        } else {
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "success", false,
                    "message", "留言添加失败"
            ));
        }
    }

    // 获取留言列表（游客开放）
    @PublicEndpoint
    @GetMapping("/list")
    public ResponseEntity<?> getMessages(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int pageSize) {
        // 分页参数边界校验
        if (page < 1) {
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "success", false,
                    "message", "page 参数必须大于等于1"
            ));
        }
        if (pageSize < 1 || pageSize > 50) {
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "success", false,
                    "message", "pageSize 范围为1~50"
            ));
        }
        Map<String, Object> maps = messageService.getMessages(page, pageSize);
        return ResponseEntity.ok().body(ImmutableMap.of(
                "success", true,
                "message", "查询成功",
                "content", maps
        ));
    }

    // 删除留言（管理员 level 0/1）
    @RequireRoles({0, 1})
    @PostMapping("/deleteMessage")
    public ResponseEntity<?> deleteMessage(@Valid @RequestBody DeleteContentRequest deleteRequest,
                                           HttpServletRequest httpRequest) {
        UserEntity loginUser = currentUser(httpRequest);
        // 传入完整用户对象至service，后续service内校验level，保持权限统一
        int affect = messageService.deleteMessage(deleteRequest.getId(), loginUser);
        if (affect > 0) {
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "success", true,
                    "message", "留言删除成功"
            ));
        } else {
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "success", false,
                    "message", "留言删除失败，资源不存在或已删除"
            ));
        }
    }

    // 添加回复
    @RequireRoles({0, 1, 2})
    @PostMapping("/addReply")
    public ResponseEntity<?> addReply(@Valid @RequestBody ReplyCreateRequest request, HttpServletRequest httpRequest) {
        UserEntity loginUser = currentUser(httpRequest);
        Integer userId = loginUser.getId();
        String content = request.getContent().trim();
        int affect = messageService.addReply(request.getMessageId(), content, userId);
        if (affect > 0) {
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "success", true,
                    "message", "回复添加成功"
            ));
        } else {
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "success", false,
                    "message", "回复添加失败，留言不存在或已删除"
            ));
        }
    }

    // 删除回复（管理员 level 0/1）
    @RequireRoles({0, 1})
    @PostMapping("/deleteReply")
    public ResponseEntity<?> deleteReply(@Valid @RequestBody DeleteContentRequest deleteRequest,
                                        HttpServletRequest httpRequest) {
        UserEntity loginUser = currentUser(httpRequest);
        int affect = messageService.deleteReply(deleteRequest.getId(), loginUser);
        if (affect > 0) {
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "success", true,
                    "message", "回复删除成功"
            ));
        } else {
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "success", false,
                    "message", "回复删除失败，资源不存在或已删除"
            ));
        }
    }

    private UserEntity currentUser(HttpServletRequest request) {
        return (UserEntity) request.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE);
    }
}