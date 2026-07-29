package com.vihu.ganlu.actions;

import com.google.common.collect.ImmutableMap;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.PublicEndpoint;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.security.TokenService;
import com.vihu.ganlu.service.CourseDetailService;
import com.vihu.ganlu.service.UserService;
import com.vihu.ganlu.utils.ResultUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/user")
public class UserAction {
    @Resource
    UserService userService;
    @Resource
    CourseDetailService courseDetailService;
    @Resource
    TokenService tokenService;

    @PublicEndpoint
    @RequestMapping("/hello")
    public String Hello(){
        return "Hello wolrd";
    }

    @PublicEndpoint
    @RequestMapping("/login")
    public ResponseEntity<?> Login(@RequestBody UserEntity e){
        UserEntity u = userService.login(e);
        if(u!=null){
            u.setPassword(null);
            String token = tokenService.createToken(u);
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "登录成功",
                    "content", u,
                    "token", token,
                    "expiresIn", tokenService.getExpirationSeconds()
            ));
        }else{
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 201,
                    "message", "账号或者密码错误"
            ));
        }
    }

    @RequireRoles({0})
    @RequestMapping("/add_team")
    public String AddTeam(@RequestBody UserEntity e){
        String mes="";
        e.setLevel(1);
        Integer integer = userService.addUser(e);
        if(integer>0){
            mes= ResultUtil.toJsonString(200, null);
        }else{
            mes=ResultUtil.toJsonString(201,null);
        }
        return mes;
    }

    @RequireRoles({0, 1})
    @RequestMapping("/update_team")
    public ResponseEntity<?> UpdateTeam(@RequestBody UserEntity e, HttpServletRequest request){
        UserEntity currentUser = currentUser(request);
        UserEntity targetUser = e.getId() == null ? null : userService.findUserById(e.getId());
        if (!canManageTarget(currentUser, targetUser)) {
            return forbidden();
        }

        // The update endpoint must not be used to promote or demote an account.
        e.setLevel(targetUser.getLevel());
        String mes="";
        Integer integer = userService.updateUserById(e);
        if(integer>0){
            mes= ResultUtil.toJsonString(200, null);
        }else{
            mes=ResultUtil.toJsonString(201,null);
        }
        return ResponseEntity.ok(mes);
    }

    @PublicEndpoint
    @RequestMapping("/add_student")
    public String AddStudent(@RequestBody UserEntity e){
        String mes="";
        e.setLevel(2);
        Integer integer = userService.addUser(e);
        if(integer>0){
            mes= ResultUtil.toJsonString(200, null);
        }else{
            mes=ResultUtil.toJsonString(201,null);
        }
        return mes;
    }

    @PublicEndpoint
    @RequestMapping("/teams")
    public String GetTeams(){
        String mes="";
        List<UserEntity> userByLevel = userService.findUserByLevel(1);
        if(userByLevel!=null){
            for (UserEntity e :
                    userByLevel) {
                e.setPassword(null);
            }
            mes= ResultUtil.toJsonString(200, userByLevel);
        }else{
            mes=ResultUtil.toJsonString(201,null);
        }
        return mes;
    }

    @RequireRoles({0, 1})
    @RequestMapping("/students")
    public String GetStudents(){
        String mes="";
        List<UserEntity> userByLevel = userService.findUserByLevel(2);
        if(userByLevel!=null){
            for (UserEntity e :
                    userByLevel) {
                e.setPassword(null);
            }
            mes= ResultUtil.toJsonString(200, userByLevel);
        }else{
            mes=ResultUtil.toJsonString(201,null);
        }
        return mes;
    }

    @RequireRoles({0, 1})
    @RequestMapping("/delete_team")
    public ResponseEntity<?> DeleteTeam(@RequestBody List<Integer> ids, HttpServletRequest request){
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "code", 400,
                    "message", "请选择要删除的账号"
            ));
        }

        UserEntity currentUser = currentUser(request);
        for (Integer id : ids) {
            if (id == null || !canManageTarget(currentUser, userService.findUserById(id))) {
                return forbidden();
            }
        }

        String mes="";
        Integer i = userService.deleteUserByIds(ids);
        if(i>0){
            mes= ResultUtil.toJsonString(200, null);
        }else{
            mes=ResultUtil.toJsonString(201,null);
        }
        return ResponseEntity.ok(mes);
    }

    private UserEntity currentUser(HttpServletRequest request) {
        return (UserEntity) request.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE);
    }

    private boolean canManageTarget(UserEntity currentUser, UserEntity targetUser) {
        if (currentUser == null || targetUser == null || targetUser.getLevel() == null) {
            return false;
        }
        if (currentUser.getLevel() == 0) {
            return targetUser.getLevel() == 1 || targetUser.getLevel() == 2;
        }
        return currentUser.getLevel() == 1 && targetUser.getLevel() == 2;
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ImmutableMap.of(
                "code", 403,
                "message", "无权管理该账号"
        ));
    }

}
