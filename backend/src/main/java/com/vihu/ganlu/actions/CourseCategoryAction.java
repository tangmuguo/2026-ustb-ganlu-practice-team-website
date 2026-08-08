package com.vihu.ganlu.actions;

import com.vihu.ganlu.entitys.CourseCategoryRequest;
import com.vihu.ganlu.entitys.CourseEntity;
import com.vihu.ganlu.security.PublicEndpoint;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.service.CourseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/courseCategory")
public class CourseCategoryAction {
    private final CourseService courseService;

    public CourseCategoryAction(CourseService courseService) {
        this.courseService = courseService;
    }

    @PublicEndpoint
    @GetMapping("/list")
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(body(200, "查询成功", courseService.getActiveCourses()));
    }

    @RequireRoles({0})
    @GetMapping("/manage")
    public ResponseEntity<?> manage() {
        return ResponseEntity.ok(body(200, "查询成功", courseService.getAllCourses()));
    }

    @RequireRoles({0})
    @PostMapping
    public ResponseEntity<?> add(@RequestBody CourseCategoryRequest request) {
        CourseEntity course = courseService.addCourse(request.getCourseName());
        return ResponseEntity.status(HttpStatus.CREATED).body(body(200, "新增成功", course));
    }

    @RequireRoles({0})
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable("id") int id, @RequestBody CourseCategoryRequest request) {
        CourseEntity course = courseService.updateCourse(id, request.getCourseName(), request.getStatus());
        return ResponseEntity.ok(body(200, "更新成功", course));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(body(400, exception.getMessage(), null));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<?> handleNotFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(body(404, exception.getMessage(), null));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleConflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body(409, exception.getMessage(), null));
    }

    private Map<String, Object> body(int code, String message, Object content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("content", content);
        return body;
    }
}
