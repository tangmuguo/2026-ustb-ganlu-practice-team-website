package com.vihu.ganlu.actions;

import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.privacy.PrivacyRequestCreateRequest;
import com.vihu.ganlu.entitys.privacy.PrivacyRequestResolutionRequest;
import com.vihu.ganlu.entitys.privacy.PrivacyRequestViewDto;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.service.AuditEventService;
import com.vihu.ganlu.service.PrivacyRequestService;
import com.vihu.ganlu.utils.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Authenticated privacy-rights ticket endpoints.  The requester identity is
 * always read from AuthInterceptor's user attribute; no request-body user id is
 * accepted, so a client cannot create or read another person's ticket.
 */
@RestController
@RequestMapping
public class PrivacyRequestAction {
    private final PrivacyRequestService privacyRequestService;
    private final AuditEventService auditEventService;

    @Autowired
    public PrivacyRequestAction(PrivacyRequestService privacyRequestService,
                                AuditEventService auditEventService) {
        this.privacyRequestService = privacyRequestService;
        this.auditEventService = auditEventService;
    }

    /** Convenience constructor for focused controller tests without a DB-backed audit service. */
    public PrivacyRequestAction(PrivacyRequestService privacyRequestService) {
        this(privacyRequestService, null);
    }

    @RequireRoles({0, 1, 2})
    @PostMapping({"/privacy-requests", "/privacy/requests"})
    public ResponseEntity<ApiResponse<Map<String, Long>>> create(
            @Valid @RequestBody PrivacyRequestCreateRequest request,
            HttpServletRequest servletRequest) {
        UserEntity actor = currentUser(servletRequest);
        long requestId = privacyRequestService.create(request, actor);
        audit(actor, "PRIVACY_REQUEST_CREATE", "PRIVACY_REQUEST", requestId,
                "SUCCESS", request.getRequestType());
        Map<String, Long> content = new LinkedHashMap<String, Long>();
        content.put("ticketId", requestId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("隐私权利工单已受理，请保存工单编号", content));
    }

    @RequireRoles({0, 1, 2})
    @GetMapping({"/privacy-requests", "/privacy-requests/mine", "/privacy/requests", "/privacy/requests/mine"})
    public ApiResponse<Map<String, Object>> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest servletRequest) {
        UserEntity actor = currentUser(servletRequest);
        List<PrivacyRequestViewDto> items = privacyRequestService.findMine(page, pageSize, actor);
        return ApiResponse.success("查询成功", pageContent(items,
                privacyRequestService.countMine(actor), page, pageSize));
    }

    @RequireRoles({0, 1, 2})
    @GetMapping({"/privacy-requests/{requestId}", "/privacy/requests/{requestId}"})
    public ApiResponse<PrivacyRequestViewDto> mineById(@PathVariable long requestId,
                                                        HttpServletRequest servletRequest) {
        PrivacyRequestViewDto request = privacyRequestService.findMineById(
                requestId, currentUser(servletRequest));
        if (request == null) throw new java.util.NoSuchElementException("隐私权利工单不存在");
        return ApiResponse.success("查询成功", request);
    }

    @RequireRoles({0})
    @GetMapping({"/admin/privacy-requests", "/admin/privacy/requests"})
    public ApiResponse<Map<String, Object>> adminList(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            HttpServletRequest servletRequest) {
        UserEntity actor = currentUser(servletRequest);
        List<PrivacyRequestViewDto> items = privacyRequestService.findRecent(status, page, pageSize, actor);
        return ApiResponse.success("查询成功", pageContent(items,
                privacyRequestService.countRecent(status, actor), page, pageSize));
    }

    @RequireRoles({0})
    @GetMapping({"/admin/privacy-requests/{requestId}", "/admin/privacy/requests/{requestId}"})
    public ApiResponse<PrivacyRequestViewDto> adminById(@PathVariable long requestId,
                                                         HttpServletRequest servletRequest) {
        PrivacyRequestViewDto request = privacyRequestService.findForAdministrator(
                requestId, currentUser(servletRequest));
        if (request == null) throw new java.util.NoSuchElementException("隐私权利工单不存在");
        return ApiResponse.success("查询成功", request);
    }

    @RequireRoles({0})
    @PutMapping({"/admin/privacy-requests/{requestId}", "/admin/privacy/requests/{requestId}"})
    public ApiResponse<Void> process(@PathVariable long requestId,
                                     @Valid @RequestBody PrivacyRequestResolutionRequest resolution,
                                     HttpServletRequest servletRequest) {
        UserEntity actor = currentUser(servletRequest);
        privacyRequestService.process(requestId, resolution, actor);
        audit(actor, "PRIVACY_REQUEST_PROCESS", "PRIVACY_REQUEST", requestId,
                "SUCCESS", resolution.getDecisionCode());
        return ApiResponse.success("隐私权利工单处理结果已保存", null);
    }

    private Map<String, Object> pageContent(List<PrivacyRequestViewDto> items, int total,
                                            int page, int pageSize) {
        Map<String, Object> content = new LinkedHashMap<String, Object>();
        content.put("items", items);
        content.put("total", total);
        content.put("page", page);
        content.put("pageSize", pageSize);
        return content;
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
