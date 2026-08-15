package com.vihu.ganlu.actions;

import com.vihu.ganlu.entitys.MessageEntity;
import com.vihu.ganlu.entitys.ReplyEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.message.ContentReviewRequest;
import com.vihu.ganlu.entitys.message.DeleteContentRequest;
import com.vihu.ganlu.entitys.message.MessageCreateRequest;
import com.vihu.ganlu.entitys.message.ReplyCreateRequest;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.PublicEndpoint;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.service.AuditEventService;
import com.vihu.ganlu.service.impl.MessageServiceImpl;
import com.vihu.ganlu.utils.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/message")
public class MessageAction {
    private final MessageServiceImpl messageService;
    private final AuditEventService auditEventService;

    @Autowired
    public MessageAction(MessageServiceImpl messageService, AuditEventService auditEventService) {
        this.messageService = messageService;
        this.auditEventService = auditEventService;
    }

    /** Retained for focused MVC tests. */
    public MessageAction(MessageServiceImpl messageService) {
        this(messageService, null);
    }

    @RequireRoles({0, 1, 2})
    @PostMapping("/add")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> addMessage(
            @Valid @RequestBody MessageCreateRequest message, HttpServletRequest request) {
        UserEntity actor = currentUser(request);
        MessageEntity created = messageService.addMessage(message, actor.getId());
        audit(actor, "MESSAGE_CREATE", "MESSAGE", created.getId(), "SUCCESS", "PENDING_REVIEW");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("留言已提交，审核通过后将公开显示",
                        Collections.singletonMap("id", created.getId())));
    }

    @PublicEndpoint
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> getMessages(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success("查询成功", messageService.getMessages(page, pageSize));
    }

    @RequireRoles({0, 1, 2})
    @PostMapping("/deleteMessage")
    public ApiResponse<Void> deleteMessage(
            @Valid @RequestBody DeleteContentRequest deleteRequest, HttpServletRequest request) {
        UserEntity actor = currentUser(request);
        try {
            messageService.deleteMessage(deleteRequest.getId(), actor.getId(), deleteRequest.getReasonCode());
            audit(actor, "MESSAGE_REMOVE", "MESSAGE", deleteRequest.getId(), "SUCCESS",
                    deleteRequest.getReasonCode() == null ? "SELF_DELETE" : deleteRequest.getReasonCode());
            return ApiResponse.success("留言已移除", null);
        } catch (SecurityException error) {
            audit(actor, "MESSAGE_REMOVE", "MESSAGE", deleteRequest.getId(), "DENIED", "NOT_OWNER_OR_MODERATOR");
            throw error;
        }
    }

    @RequireRoles({0, 1, 2})
    @PostMapping("/addReply")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> addReply(
            @Valid @RequestBody ReplyCreateRequest reply, HttpServletRequest request) {
        UserEntity actor = currentUser(request);
        ReplyEntity created = messageService.addReply(reply, actor.getId());
        audit(actor, "REPLY_CREATE", "REPLY", created.getId(), "SUCCESS", "PENDING_REVIEW");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("回复已提交，审核通过后将公开显示",
                        Collections.singletonMap("id", created.getId())));
    }

    @RequireRoles({0, 1, 2})
    @PostMapping("/deleteReply")
    public ApiResponse<Void> deleteReply(
            @Valid @RequestBody DeleteContentRequest deleteRequest, HttpServletRequest request) {
        UserEntity actor = currentUser(request);
        try {
            messageService.deleteReply(deleteRequest.getId(), actor.getId(), deleteRequest.getReasonCode());
            audit(actor, "REPLY_REMOVE", "REPLY", deleteRequest.getId(), "SUCCESS",
                    deleteRequest.getReasonCode() == null ? "SELF_DELETE" : deleteRequest.getReasonCode());
            return ApiResponse.success("回复已移除", null);
        } catch (SecurityException error) {
            audit(actor, "REPLY_REMOVE", "REPLY", deleteRequest.getId(), "DENIED", "NOT_OWNER_OR_MODERATOR");
            throw error;
        }
    }

    @RequireRoles({0})
    @GetMapping("/moderation/pending")
    public ApiResponse<Map<String, Object>> pendingContent(
            @RequestParam(defaultValue = "MESSAGE") String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ApiResponse.success("查询成功",
                messageService.getPendingContent(type, page, pageSize, currentUser(request)));
    }

    @RequireRoles({0})
    @PostMapping("/moderation/review")
    public ApiResponse<Void> reviewContent(
            @Valid @RequestBody ContentReviewRequest review, HttpServletRequest request) {
        UserEntity actor = currentUser(request);
        try {
            messageService.reviewContent(review, actor);
            audit(actor, "CONTENT_REVIEW", review.getContentType(), review.getContentId(), "SUCCESS",
                    review.getDecision() + "_" + review.getReasonCode());
            return ApiResponse.success("审核处置已保存", null);
        } catch (SecurityException error) {
            audit(actor, "CONTENT_REVIEW", review.getContentType(), review.getContentId(), "DENIED", "NOT_MODERATOR");
            throw error;
        }
    }

    private UserEntity currentUser(HttpServletRequest request) {
        return (UserEntity) request.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE);
    }

    private void audit(UserEntity actor, String action, String resourceType, Object resourceId,
                       String outcome, String reasonCode) {
        if (auditEventService != null) {
            auditEventService.record(actor, action, resourceType, resourceId, outcome, reasonCode);
        }
    }
}
