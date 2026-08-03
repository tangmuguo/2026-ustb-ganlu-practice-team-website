package com.vihu.ganlu.actions;

import com.vihu.ganlu.entitys.LoginRequest;
import com.vihu.ganlu.entitys.LoginResponse;
import com.vihu.ganlu.entitys.StudentRegistrationRequest;
import com.vihu.ganlu.entitys.TeamRegistrationRequest;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.UserSummary;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.PublicEndpoint;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.security.TokenService;
import com.vihu.ganlu.service.UserService;
import com.vihu.ganlu.utils.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
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

    public UserAction(UserService userService, TokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
    }

    @PublicEndpoint
    @RequestMapping("/hello")
    public ApiResponse<String> hello() {
        return ApiResponse.success("服务正常", "Hello world");
    }

    @PublicEndpoint
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        UserEntity user = userService.authenticate(request.getUsername(), request.getPassword());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "账号或密码错误"));
        }

        String token = tokenService.createToken(user);
        LoginResponse response = new LoginResponse(
                token,
                tokenService.getExpirationSeconds(),
                UserSummary.from(user));
        return ResponseEntity.ok(ApiResponse.success("登录成功", response));
    }

    @RequireRoles({0})
    @PostMapping("/add_team")
    public ResponseEntity<ApiResponse<UserSummary>> addTeam(@Valid @RequestBody TeamRegistrationRequest request) {
        requireMatchingPasswords(request.getPassword(), request.getConfirmPassword());
        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setTeamname(request.getTeamname());
        user.setHelplocation(request.getHelplocation());
        user.setHelpschool(request.getHelpschool());
        user.setPhone(request.getPhone());
        user.setPassword(request.getPassword());
        user.setLevel(1);
        userService.addUser(user);
        return ResponseEntity.ok(ApiResponse.success("团队账号创建成功", UserSummary.from(user)));
    }

    @PublicEndpoint
    @PostMapping("/register/student")
    public ResponseEntity<ApiResponse<UserSummary>> registerStudent(
            @Valid @RequestBody StudentRegistrationRequest request) {
        requireMatchingPasswords(request.getPassword(), request.getConfirmPassword());
        UserEntity user = toStudent(request);
        userService.addUser(user);
        return ResponseEntity.ok(ApiResponse.success("学生账号注册成功", UserSummary.from(user)));
    }

    // 兼容现有学生管理页面；只有管理员和团队账号可以使用。
    @RequireRoles({0, 1})
    @PostMapping("/add_student")
    public ResponseEntity<ApiResponse<UserSummary>> addStudentFromManagement(@RequestBody UserEntity user) {
        user.setLevel(2);
        userService.addUser(user);
        return ResponseEntity.ok(ApiResponse.success("学生账号创建成功", UserSummary.from(user)));
    }

    @RequireRoles({0, 1})
    @PostMapping("/update_team")
    public ResponseEntity<ApiResponse<Void>> updateUser(
            @RequestBody UserEntity user,
            HttpServletRequest request) {
        UserEntity currentUser = currentUser(request);
        UserEntity targetUser = user.getId() == null ? null : userService.findUserById(user.getId());
        if (!canManageTarget(currentUser, targetUser)) return forbidden();

        user.setLevel(targetUser.getLevel());
        user.setImageUploadUserId(currentUser.getId());
        int updated = userService.updateUserById(user);
        return updated > 0
                ? ResponseEntity.ok(ApiResponse.success("账号更新成功", null))
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "账号不存在"));
    }

    @RequireRoles({0})
    @PostMapping("/teams")
    public ApiResponse<List<UserSummary>> getTeams() {
        return ApiResponse.success("查询成功", summaries(userService.findUserByLevel(1)));
    }

    @RequireRoles({0, 1})
    @PostMapping("/students")
    public ApiResponse<List<UserSummary>> getStudents() {
        return ApiResponse.success("查询成功", summaries(userService.findUserByLevel(2)));
    }

    @RequireRoles({0, 1})
    @PostMapping("/delete_team")
    public ResponseEntity<ApiResponse<Void>> deleteUsers(
            @RequestBody List<Integer> ids,
            HttpServletRequest request) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "请选择要删除的账号"));
        }

        UserEntity currentUser = currentUser(request);
        for (Integer id : ids) {
            if (id == null || !canManageTarget(currentUser, userService.findUserById(id))) return forbidden();
        }

        int deleted = userService.deleteUserByIds(ids);
        return deleted > 0
                ? ResponseEntity.ok(ApiResponse.success("账号删除成功", null))
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "账号不存在"));
    }

    private UserEntity toStudent(StudentRegistrationRequest request) {
        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setRealname(request.getRealname());
        user.setBelongschool(request.getBelongschool());
        user.setGrade(request.getGrade());
        user.setPhone(request.getPhone());
        user.setPassword(request.getPassword());
        user.setLevel(2);
        return user;
    }

    private void requireMatchingPasswords(String password, String confirmPassword) {
        if (password == null || !password.equals(confirmPassword)) {
            throw new IllegalArgumentException("两次输入的密码不一致");
        }
    }

    private List<UserSummary> summaries(List<UserEntity> users) {
        List<UserSummary> result = new ArrayList<>();
        if (users != null) {
            for (UserEntity user : users) result.add(UserSummary.from(user));
        }
        return result;
    }

    private UserEntity currentUser(HttpServletRequest request) {
        return (UserEntity) request.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE);
    }

    private boolean canManageTarget(UserEntity currentUser, UserEntity targetUser) {
        if (currentUser == null || targetUser == null || targetUser.getLevel() == null) return false;
        if (currentUser.getLevel() == 0) return targetUser.getLevel() == 1 || targetUser.getLevel() == 2;
        return currentUser.getLevel() == 1 && targetUser.getLevel() == 2;
    }

    private ResponseEntity<ApiResponse<Void>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(403, "无权管理该账号"));
    }
}
