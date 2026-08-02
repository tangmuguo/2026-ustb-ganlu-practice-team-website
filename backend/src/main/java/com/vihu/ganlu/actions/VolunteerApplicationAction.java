package com.vihu.ganlu.actions;

import com.vihu.ganlu.entitys.VolunteerApplicationEntity;
import com.vihu.ganlu.entitys.VolunteerApplicationRequest;
import com.vihu.ganlu.entitys.VolunteerApplicationStatusRequest;
import com.vihu.ganlu.security.PublicEndpoint;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.service.VolunteerApplicationService;
import com.vihu.ganlu.service.impl.VolunteerApplicationServiceImpl.DuplicateApplicationException;
import com.vihu.ganlu.utils.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class VolunteerApplicationAction {
    private final VolunteerApplicationService service;

    public VolunteerApplicationAction(VolunteerApplicationService service) {
        this.service = service;
    }

    @PublicEndpoint
    @PostMapping("/volunteer-applications")
    public ApiResponse<Map<String, Object>> submit(@Valid @RequestBody VolunteerApplicationRequest request) {
        VolunteerApplicationEntity entity = service.submit(request);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("id", entity.getId());
        content.put("status", entity.getStatus());
        return ApiResponse.success("报名信息已提交", content);
    }

    @RequireRoles({0})
    @GetMapping("/admin/volunteer-applications")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("分页参数不正确");
        }
        List<VolunteerApplicationEntity> items = service.findPage(status, page, pageSize);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("items", items);
        content.put("total", service.count(status));
        content.put("page", page);
        content.put("pageSize", pageSize);
        return ApiResponse.success("查询成功", content);
    }

    @RequireRoles({0})
    @PatchMapping("/admin/volunteer-applications/{id}/status")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody VolunteerApplicationStatusRequest request) {
        if (!service.updateStatus(id, request.getStatus())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "报名记录不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success("状态已更新", null));
    }

    @ExceptionHandler(DuplicateApplicationException.class)
    public ResponseEntity<ApiResponse<Void>> duplicate(DuplicateApplicationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(409, ex.getMessage()));
    }
}
