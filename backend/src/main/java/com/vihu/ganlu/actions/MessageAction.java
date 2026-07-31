package com.vihu.ganlu.actions;

import com.google.common.collect.ImmutableMap;
import com.vihu.ganlu.entitys.message.DeleteContentRequest;
import com.vihu.ganlu.entitys.MessageEntity;
import com.vihu.ganlu.entitys.ReplyEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.PublicEndpoint;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.service.impl.MessageServiceImpl;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/message")
public class MessageAction {
    @Autowired
    private MessageServiceImpl messageService;

    // 添加留言
    @RequireRoles({0, 1, 2})
    @PostMapping("/add")
    public ResponseEntity<?> addMessage(@RequestBody MessageEntity message, HttpServletRequest request) {
        Integer userId = currentUser(request).getId();
        int i = messageService.addMessage(message, userId);
        if(i>0){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "success", true,
                    "message", "添加成功"
            ));
        }else{
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "success", false,
                    "message", "添加失败"
            ));
        }
    }

    // 获取留言列表
    @PublicEndpoint
    @GetMapping("/list")
    public ResponseEntity<?> getMessages(@RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "10") int pageSize) {
        Map<String, Object> maps = messageService.getMessages(page, pageSize);
        System.out.println(maps);
        return ResponseEntity.ok().body(ImmutableMap.of(
                "success", true,
                "message", "添加成功",
                "content", maps
        ));
    }

    // 删除留言（管理员）
    @RequireRoles({0, 1})
    @RequestMapping("/deleteMessage")
    public ResponseEntity<?> deleteMessage(@RequestBody DeleteContentRequest deleteRequest,
                                           HttpServletRequest request) {

        int i = messageService.deleteMessage(deleteRequest.getId(), currentUser(request).getId());
        if(i>0){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "success", true,
                    "message", "删除成功"
            ));
        }else{
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "success", false,
                    "message", "删除失败"
            ));
        }
    }

    // 添加回复
    @RequireRoles({0, 1, 2})
    @PostMapping("/addReply")
    public ResponseEntity<?> addReply(@RequestBody ReplyEntity reply, HttpServletRequest request) {
        Integer userId = currentUser(request).getId();
        int i = messageService.addReply(reply, userId);
        if(i>0){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "success", true,
                    "message", "添加成功"
            ));
        }else{
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "success", false,
                    "message", "添加失败"
            ));
        }
    }

    // 删除回复（管理员）
    @RequireRoles({0, 1})
    @RequestMapping("/deleteReply")
    public ResponseEntity<?> deleteReply(@RequestBody DeleteContentRequest deleteRequest,
                                         HttpServletRequest request) {
        int i = messageService.deleteReply(deleteRequest.getId(), currentUser(request).getId());
        if(i>0){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "success", true,
                    "message", "删除成功"
            ));
        }else{
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "success", false,
                    "message", "删除失败"
            ));
        }
    }

    private UserEntity currentUser(HttpServletRequest request) {
        return (UserEntity) request.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE);
    }
}


