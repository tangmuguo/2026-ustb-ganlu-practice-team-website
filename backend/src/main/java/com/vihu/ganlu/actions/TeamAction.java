package com.vihu.ganlu.actions;

import com.vihu.ganlu.entitys.ResultEntity;
import com.vihu.ganlu.entitys.TeamDetailDto;
import com.vihu.ganlu.entitys.TeamSaveRequest;
import com.vihu.ganlu.security.PublicEndpoint;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.service.TeamServie;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.NoSuchElementException;

@RestController
public class TeamAction {
    private final TeamServie teamServie;

    public TeamAction(TeamServie teamServie) {
        this.teamServie = teamServie;
    }

    @PublicEndpoint
    @GetMapping("/teams/years")
    public ResponseEntity<ResultEntity> getYears() {
        return success("查询成功", teamServie.getPublishedYears());
    }

    @PublicEndpoint
    @GetMapping("/teams")
    public ResponseEntity<ResultEntity> getTeams(
            @RequestParam String year,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        return success("查询成功", teamServie.getPublishedTeams(year, page, size));
    }

    @PublicEndpoint
    @GetMapping("/teams/{teamId}")
    public ResponseEntity<ResultEntity> getTeamDetail(@PathVariable int teamId) {
        TeamDetailDto detail = teamServie.getPublishedTeamDetail(teamId);
        if (detail == null) {
            return error(HttpStatus.NOT_FOUND, "小队不存在或未发布");
        }
        return success("查询成功", detail);
    }

    @RequireRoles({0})
    @PostMapping("/admin/teams")
    public ResponseEntity<ResultEntity> createTeam(@RequestBody TeamSaveRequest request) {
        return success("创建成功", teamServie.createTeam(request));
    }

    @RequireRoles({0})
    @PutMapping("/admin/teams/{teamId}")
    public ResponseEntity<ResultEntity> updateTeam(
            @PathVariable int teamId,
            @RequestBody TeamSaveRequest request) {
        return success("更新成功", teamServie.updateTeam(teamId, request));
    }

    @RequireRoles({0})
    @DeleteMapping("/admin/teams/{teamId}")
    public ResponseEntity<ResultEntity> archiveTeam(@PathVariable int teamId) {
        teamServie.archiveTeam(teamId);
        return success("归档成功", null);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ResultEntity> handleDuplicate(DuplicateKeyException exception) {
        return error(HttpStatus.CONFLICT, "同一年份下已存在同名小队");
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ResultEntity> handleNotFound(NoSuchElementException exception) {
        return error(HttpStatus.NOT_FOUND, safeMessage(exception, "小队不存在"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResultEntity> handleBadRequest(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, safeMessage(exception, "请求参数不合法"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ResultEntity> handleIllegalState(IllegalStateException exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, safeMessage(exception, "小队操作失败"));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ResultEntity> handleMalformedRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "请求参数格式不正确");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResultEntity> handleUnexpected(Exception exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "服务器暂时无法处理请求");
    }

    private ResponseEntity<ResultEntity> success(String message, Object content) {
        ResultEntity result = result(HttpStatus.OK.value(), message, content);
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<ResultEntity> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(result(status.value(), message, null));
    }

    private ResultEntity result(int code, String message, Object content) {
        ResultEntity result = new ResultEntity();
        result.setCode(code);
        result.setMessage(message);
        result.setContent(content);
        return result;
    }

    private String safeMessage(RuntimeException exception, String fallback) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? fallback : message;
    }
}
