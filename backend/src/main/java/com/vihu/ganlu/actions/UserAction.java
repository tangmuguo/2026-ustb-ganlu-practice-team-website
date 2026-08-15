package com.vihu.ganlu.actions;

import com.vihu.ganlu.entitys.AdminStudentDetailDto;
import com.vihu.ganlu.entitys.LoginRequest;
import com.vihu.ganlu.entitys.LoginResponse;
import com.vihu.ganlu.entitys.StudentListItemDto;
import com.vihu.ganlu.entitys.StudentProvisionRequest;
import com.vihu.ganlu.entitys.StudentUpdateRequest;
import com.vihu.ganlu.entitys.StudentVerificationRequest;
import com.vihu.ganlu.entitys.TeamRegistrationRequest;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.UserSummary;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.PublicEndpoint;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.security.TokenService;
import com.vihu.ganlu.service.AuditEventService;
import com.vihu.ganlu.service.UserService;
import com.vihu.ganlu.utils.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/user")
public class UserAction {
    private final UserService userService;
    private final TokenService tokenService;
    private final AuditEventService auditEventService;

    @Autowired
    public UserAction(UserService userService, TokenService tokenService, AuditEventService auditEventService) {
        this.userService = userService;
        this.tokenService = tokenService;
        this.auditEventService = auditEventService;
    }

    /** Retained for focused tests that do not exercise persistence-backed audit logging. */
    public UserAction(UserService userService, TokenService tokenService) {
        this(userService, tokenService, null);
    }

    @PublicEndpoint
    @GetMapping("/hello")
    public ApiResponse<String> hello() {
        return ApiResponse.success("服务正常", "Hello world");
    }

    @PublicEndpoint
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        UserEntity user = userService.authenticate(request.getUsername(), request.getPassword());
        if (user == null) {
            audit(null, "LOGIN_FAILURE", "ACCOUNT", null, "DENIED", "INVALID_CREDENTIALS");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .body(ApiResponse.error(401, "账号或密码错误"));
        }

        LoginResponse response = new LoginResponse(
                tokenService.createToken(user),
                tokenService.getExpirationSeconds(),
                UserSummary.from(user));
        audit(user, "LOGIN_SUCCESS", "ACCOUNT", user.getId(), "SUCCESS", null);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("登录成功", response));
    }

    @RequireRoles({0})
    @PostMapping("/add_team")
    public ResponseEntity<ApiResponse<UserSummary>> addTeam(@Valid @RequestBody TeamRegistrationRequest request,
                                                             HttpServletRequest servletRequest) {
        requireMatchingPasswords(request.getPassword(), request.getConfirmPassword());
        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setTeamname(request.getTeamname());
        user.setHelplocation(request.getHelplocation());
        user.setHelpschool(request.getHelpschool());
        user.setPhone(request.getPhone());
        user.setPassword(request.getPassword());
        user.setLevel(1);
        user.setVerificationStatus("VERIFIED");
        user.setGuardianConsentStatus("NOT_REQUIRED");
        user.setSessionVersion(0);
        userService.addUser(user);
        audit(currentUser(servletRequest), "TEAM_ACCOUNT_CREATE", "USER", user.getId(), "SUCCESS", null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("团队账号创建成功", UserSummary.from(user)));
    }

    /** Student accounts are provisioned only by an administrator or their assigned team. */
    @RequireRoles({0, 1})
    @PostMapping("/students")
    public ResponseEntity<ApiResponse<StudentListItemDto>> provisionStudent(
            @Valid @RequestBody StudentProvisionRequest request,
            HttpServletRequest servletRequest) {
        UserEntity actor = currentUser(servletRequest);
        StudentListItemDto student = userService.provisionStudent(request, actor);
        audit(actor, "STUDENT_PROVISION", "USER", student.getId(), "SUCCESS", "PENDING_VERIFICATION");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("学生账号已创建，待完成线下核验和监护人授权", student));
    }

    @RequireRoles({0, 1})
    @GetMapping("/students")
    public ApiResponse<List<StudentListItemDto>> getStudents(HttpServletRequest servletRequest) {
        UserEntity actor = currentUser(servletRequest);
        List<StudentListItemDto> students = userService.findManageableStudents(actor);
        audit(actor, "STUDENT_LIST", "USER", null, "SUCCESS", null);
        return ApiResponse.success("查询成功", students);
    }

    @RequireRoles({0, 1})
    @PutMapping("/students/{studentId}")
    public ResponseEntity<ApiResponse<Void>> updateStudent(
            @PathVariable int studentId,
            @Valid @RequestBody StudentUpdateRequest request,
            HttpServletRequest servletRequest) {
        UserEntity actor = currentUser(servletRequest);
        try {
            userService.updateStudent(studentId, request, actor);
            audit(actor, "STUDENT_UPDATE", "USER", studentId, "SUCCESS", null);
        } catch (SecurityException error) {
            audit(actor, "STUDENT_UPDATE", "USER", studentId, "DENIED", "TENANT_BOUNDARY");
            throw error;
        }
        return ResponseEntity.ok(ApiResponse.success("学生账号更新成功", null));
    }

    @RequireRoles({0, 1})
    @DeleteMapping("/students")
    public ResponseEntity<ApiResponse<Void>> deleteStudents(
            @RequestBody List<Integer> ids,
            HttpServletRequest servletRequest) {
        UserEntity actor = currentUser(servletRequest);
        try {
            userService.deleteStudents(ids, actor);
            audit(actor, "STUDENT_DELETE", "USER", ids.toString(), "SUCCESS", null);
        } catch (SecurityException error) {
            audit(actor, "STUDENT_DELETE", "USER", ids.toString(), "DENIED", "TENANT_BOUNDARY");
            throw error;
        }
        return ResponseEntity.ok(ApiResponse.success("学生账号删除成功", null));
    }

    @RequireRoles({0})
    @GetMapping("/admin/students/{studentId}")
    public ApiResponse<AdminStudentDetailDto> getStudentForAdministrator(
            @PathVariable int studentId,
            HttpServletRequest servletRequest) {
        UserEntity actor = currentUser(servletRequest);
        AdminStudentDetailDto student = userService.findStudentForAdministrator(studentId, actor);
        audit(actor, "STUDENT_ADMIN_DETAIL", "USER", studentId, "SUCCESS", null);
        return ApiResponse.success("查询成功", student);
    }

    @RequireRoles({0})
    @PutMapping("/admin/students/{studentId}/verification")
    public ApiResponse<Void> updateStudentVerification(
            @PathVariable int studentId,
            @Valid @RequestBody StudentVerificationRequest request,
            HttpServletRequest servletRequest) {
        UserEntity actor = currentUser(servletRequest);
        userService.updateStudentVerification(studentId, request, actor);
        audit(actor, "STUDENT_VERIFICATION_UPDATE", "USER", studentId, "SUCCESS",
                request.getVerificationStatus() + "_" + request.getGuardianConsentStatus());
        return ApiResponse.success("学生核验与授权记录已更新", null);
    }

    @RequireRoles({0, 1, 2})
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest servletRequest) {
        UserEntity actor = currentUser(servletRequest);
        userService.revokeCurrentSession(actor);
        audit(actor, "LOGOUT", "ACCOUNT", actor.getId(), "SUCCESS", null);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("已退出登录", null));
    }

    @RequireRoles({0})
    @GetMapping("/teams")
    public ApiResponse<List<UserSummary>> getTeams() {
        return ApiResponse.success("查询成功", summaries(userService.findUserByLevel(1)));
    }

    /** Compatibility endpoint for the existing administrator team-account screen. */
    @RequireRoles({0})
    @PutMapping("/teams/{teamAccountId}")
    public ResponseEntity<ApiResponse<Void>> updateTeamAccount(
            @PathVariable int teamAccountId,
            @RequestBody UserEntity update,
            HttpServletRequest servletRequest) {
        UserEntity target = userService.findUserById(teamAccountId);
        if (target == null || target.getLevel() == null || target.getLevel() != 1) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "团队账号不存在"));
        }
        update.setId(teamAccountId);
        update.setLevel(1);
        update.setImageUploadUserId(currentUser(servletRequest).getId());
        int changed = userService.updateUserById(update);
        if (changed != 1) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "团队账号不存在"));
        }
        audit(currentUser(servletRequest), "TEAM_ACCOUNT_UPDATE", "USER", teamAccountId, "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.success("团队账号更新成功", null));
    }

    @RequireRoles({0})
    @DeleteMapping("/teams")
    public ResponseEntity<ApiResponse<Void>> deleteTeamAccounts(@RequestBody List<Integer> ids,
                                                                 HttpServletRequest servletRequest) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "请选择要删除的团队账号"));
        }
        for (Integer id : ids) {
            UserEntity target = id == null ? null : userService.findUserById(id);
            if (target == null || target.getLevel() == null || target.getLevel() != 1) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "团队账号不存在"));
            }
        }
        userService.deleteUserByIds(ids);
        audit(currentUser(servletRequest), "TEAM_ACCOUNT_DELETE", "USER", ids.toString(), "SUCCESS", null);
        return ResponseEntity.ok(ApiResponse.success("团队账号删除成功", null));
    }

    private void requireMatchingPasswords(String password, String confirmPassword) {
        if (password == null || !password.equals(confirmPassword)) {
            throw new IllegalArgumentException("两次输入的密码不一致");
        }
    }

    private List<UserSummary> summaries(List<UserEntity> users) {
        List<UserSummary> result = new ArrayList<UserSummary>();
        if (users != null) {
            for (UserEntity user : users) result.add(UserSummary.from(user));
        }
        return result;
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
