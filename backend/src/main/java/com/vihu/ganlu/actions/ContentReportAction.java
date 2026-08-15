package com.vihu.ganlu.actions;

import com.vihu.ganlu.entitys.ContentReportEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.report.ContentReportCreateRequest;
import com.vihu.ganlu.entitys.report.ContentReportResolutionRequest;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.PublicEndpoint;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.security.TokenService;
import com.vihu.ganlu.service.AuditEventService;
import com.vihu.ganlu.service.ContentReportService;
import com.vihu.ganlu.service.UserService;
import com.vihu.ganlu.utils.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
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

@RestController
@RequestMapping
public class ContentReportAction {
    private final ContentReportService contentReportService;
    private final AuditEventService auditEventService;
    private final TokenService tokenService;
    private final UserService userService;

    @Autowired
    public ContentReportAction(ContentReportService contentReportService, AuditEventService auditEventService,
                               TokenService tokenService, UserService userService) {
        this.contentReportService = contentReportService;
        this.auditEventService = auditEventService;
        this.tokenService = tokenService;
        this.userService = userService;
    }

    /** Retained for focused MVC tests. */
    public ContentReportAction(ContentReportService contentReportService, AuditEventService auditEventService) {
        this(contentReportService, auditEventService, null, null);
    }

    /**
     * A public report endpoint deliberately accepts anonymous submissions.  A
     * valid bearer token is used opportunistically to associate only the internal
     * user id; an invalid/missing token is treated as anonymous.
     */
    @PublicEndpoint
    @PostMapping("/reports")
    public ResponseEntity<ApiResponse<Map<String, Long>>> create(
            @Valid @RequestBody ContentReportCreateRequest report, HttpServletRequest request) {
        UserEntity actor = optionalCurrentUser(request);
        try {
            long reportId = contentReportService.create(report, actor);
            audit(actor, "CONTENT_REPORT_CREATE", report.getTargetType(), report.getTargetId(), "SUCCESS", report.getCategory());
            Map<String, Long> response = new LinkedHashMap<String, Long>();
            response.put("ticketId", reportId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("举报已受理，请保存工单编号", response));
        } catch (RuntimeException error) {
            audit(actor, "CONTENT_REPORT_CREATE", report == null ? null : report.getTargetType(),
                    report == null ? null : report.getTargetId(), "FAILED", "VALIDATION_FAILED");
            throw error;
        }
    }

    @RequireRoles({0})
    @GetMapping("/admin/reports")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            HttpServletRequest request) {
        UserEntity actor = currentUser(request);
        List<ContentReportEntity> reports = contentReportService.findRecent(status, page, pageSize, actor);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("items", reports);
        result.put("total", contentReportService.countRecent(status, actor));
        result.put("page", page);
        result.put("pageSize", pageSize);
        return ApiResponse.success("查询成功", result);
    }

    @RequireRoles({0})
    @PutMapping("/admin/reports/{reportId}")
    public ApiResponse<Void> resolve(@PathVariable long reportId,
                                     @Valid @RequestBody ContentReportResolutionRequest resolution,
                                     HttpServletRequest request) {
        UserEntity actor = currentUser(request);
        try {
            contentReportService.resolve(reportId, resolution, actor);
            audit(actor, "CONTENT_REPORT_RESOLVE", "CONTENT_REPORT", reportId, "SUCCESS",
                    resolution.getStatus() + "_" + resolution.getResolutionCode());
            return ApiResponse.success("工单处置已保存", null);
        } catch (RuntimeException error) {
            audit(actor, "CONTENT_REPORT_RESOLVE", "CONTENT_REPORT", reportId, "FAILED", "VALIDATION_FAILED");
            throw error;
        }
    }

    private UserEntity currentUser(HttpServletRequest request) {
        return (UserEntity) request.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE);
    }

    private UserEntity optionalCurrentUser(HttpServletRequest request) {
        UserEntity attributeUser = currentUser(request);
        if (attributeUser != null) return attributeUser;
        if (tokenService == null || userService == null) return null;

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isEmpty()) return null;
        try {
            Integer userId = tokenService.verifyAndGetUserId(token);
            UserEntity user = userService.findUserById(userId);
            return tokenService.isTokenCurrent(token, user) ? user : null;
        } catch (RuntimeException ignored) {
            // The public endpoint must remain usable for anonymous reporters and
            // must never reveal token parsing/account state through this path.
            return null;
        }
    }

    private void audit(UserEntity actor, String action, String resourceType, Object resourceId,
                       String outcome, String reasonCode) {
        if (auditEventService != null) {
            auditEventService.record(actor, action, resourceType, resourceId, outcome, reasonCode);
        }
    }
}
