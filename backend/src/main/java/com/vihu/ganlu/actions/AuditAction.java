package com.vihu.ganlu.actions;

import com.vihu.ganlu.entitys.AuditEventEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.service.AuditEventService;
import com.vihu.ganlu.utils.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/admin/audit-events")
public class AuditAction {
    private final AuditEventService auditEventService;

    public AuditAction(AuditEventService auditEventService) { this.auditEventService = auditEventService; }

    @RequireRoles({0})
    @GetMapping
    public ApiResponse<Object> list(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "50") int pageSize,
                                    HttpServletRequest request) {
        List<AuditEventEntity> events = auditEventService.findRecent(page, pageSize);
        java.util.Map<String, Object> content = new java.util.LinkedHashMap<String, Object>();
        content.put("items", events);
        content.put("total", auditEventService.countRecent());
        content.put("page", page);
        content.put("pageSize", pageSize);
        audit(currentUser(request), "AUDIT_EVENT_LIST", "AUDIT_EVENT", null, "SUCCESS", null);
        return ApiResponse.success("查询成功", content);
    }

    @RequireRoles({0})
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "200") int pageSize,
                                         HttpServletRequest request) {
        StringBuilder csv = new StringBuilder("id,occurredAt,requestId,actorUserId,actorRole,action,resourceType,resourceId,outcome,reasonCode\n");
        for (AuditEventEntity event : auditEventService.findRecent(page, pageSize)) {
            csv.append(csv(event.getId())).append(',').append(csv(event.getOccurredAt())).append(',')
                    .append(csv(event.getRequestId())).append(',').append(csv(event.getActorUserId())).append(',')
                    .append(csv(event.getActorRole())).append(',').append(csv(event.getAction())).append(',')
                    .append(csv(event.getResourceType())).append(',').append(csv(event.getResourceId())).append(',')
                    .append(csv(event.getOutcome())).append(',').append(csv(event.getReasonCode())).append('\n');
        }
        audit(currentUser(request), "AUDIT_EVENT_EXPORT", "AUDIT_EVENT", null, "SUCCESS", null);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ganlu-audit-events.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String csv(Object value) {
        if (value == null) return "";
        return '"' + String.valueOf(value).replace("\"", "\"\"") + '"';
    }

    private UserEntity currentUser(HttpServletRequest request) {
        return (UserEntity) request.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE);
    }

    private void audit(UserEntity actor, String action, String resourceType, Object resourceId,
                       String outcome, String reasonCode) {
        auditEventService.record(actor, action, resourceType, resourceId, outcome, reasonCode);
    }
}
