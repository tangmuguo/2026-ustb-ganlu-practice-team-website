package com.vihu.ganlu.actions;

import com.github.pagehelper.PageInfo;
import com.google.common.collect.ImmutableMap;
import com.vihu.ganlu.entitys.CourseDetailEntity;
import com.vihu.ganlu.entitys.CourseEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.PublicEndpoint;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.service.CourseDetailService;
import com.vihu.ganlu.service.CourseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/courseDetail")
public class CourseDetailAction {
    private static final Pattern FILE_IDENTIFIER = Pattern.compile("^[a-fA-F0-9]{32}$");
    @Resource
    CourseDetailService courseDetailService;
    @Resource
    CourseService courseService;

    @PublicEndpoint
    @RequestMapping("/getDetail")
    public  ResponseEntity<?> getMaterialDetail(int id){
        CourseDetailEntity courseById = courseDetailService.getCourseById(id);
        if(courseById!=null){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "success", true,
                    "message", "查询成功",
                    "content",courseById
            ));
        }else{
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "success", false,
                    "message", "查询失败"
            ));
        }
    }

    /**
     * 一次性上传包括图片和文件本身
     * @param title
     * @param courseId
     * @param imageFile
     * @param courseFile
     * @return
     */
    @RequireRoles({0, 1})
    @PostMapping("/materials")
    public ResponseEntity<?> uploadMaterials(
            @RequestParam("title") String title,
            @RequestParam("courseId") Integer courseId,
            @RequestParam("imageFile") MultipartFile imageFile,
            @RequestParam("courseFile") MultipartFile courseFile,
            HttpServletRequest request) {

        CourseDetailEntity courseDetail = new CourseDetailEntity();
        courseDetail.setTitle(title);
        courseDetail.setCourseId(courseId);
        courseDetail.setAuthor(authorName(currentUser(request)));

        boolean success = courseDetailService.uploadCourseMaterial(courseDetail, imageFile, courseFile);

        if (success) {
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "success", true,
                    "message", "上传成功"
            ));
        } else {
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "success", false,
                    "message", "上传失败"
            ));
        }
    }

    /**
     * 已经上传好图片与文件，图片、文件字段已经是服务器中的文件名，直接保存即可
     * @param entity
     * @return
     */
    @RequireRoles({0, 1})
    @PostMapping("/material")
    public ResponseEntity<?> uploadMaterial(@RequestBody CourseDetailEntity entity, HttpServletRequest request) {

        entity.setAuthor(authorName(currentUser(request)));

        int success = courseDetailService.insertCourseDetail(entity);

        if (success>0) {
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "上传成功"
            ));
        } else {
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "code", 400,
                    "message", "上传失败"
            ));
        }
    }

    @RequireRoles({0, 1})
    @PostMapping("/uploadImage")
    public ResponseEntity<?> uploadImage(
            @RequestParam("imageFile") MultipartFile imageFile) {

        String imagePath = courseDetailService.uploadImage(imageFile);

        if (imagePath!=null) {
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "上传成功",
                    "content",imagePath
            ));
        } else {
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "code", 400,
                    "message", "上传失败"
            ));
        }
    }

    @RequireRoles({0, 1})
    @PostMapping("/uploadFile")
    public ResponseEntity<?> uploadFile(
            @RequestParam("materialFile") MultipartFile materialFile) {

        String materialPath = courseDetailService.uploadCourseFile(materialFile);

        if (materialPath!=null) {
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "上传成功",
                    "content",materialPath
            ));
        } else {
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "code", 400,
                    "message", "上传失败"
            ));
        }
    }

    @PublicEndpoint
    @PostMapping("/all")
    public ResponseEntity<?> findAll(){
        List<CourseDetailEntity> allCourse = courseDetailService.findAllCourse();
        if(allCourse!=null){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "查询成功",
                    "content",allCourse
            ));
        }else{
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "code", 400,
                    "message", "查询失败"
            ));
        }
    }

    @PublicEndpoint
    @RequestMapping("/list")
    public ResponseEntity<?> findCourseList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ){
        PageInfo<CourseDetailEntity> courseList = courseDetailService.getCourseList(page, size);
        if(courseList!=null){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "查询成功",
                    "content",courseList
            ));
        }else{
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "code", 400,
                    "message", "查询失败"
            ));
        }
    }

    @RequireRoles({0, 1})
    @PostMapping("/uploadChunk")
    public ResponseEntity<?> uploadChunk(
            @RequestParam("file") MultipartFile chunk,
            @RequestParam("chunkNumber") int chunkNumber,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("identifier") String identifier,
            @RequestParam("filename") String filename) throws IOException {

        if (!validIdentifier(identifier) || chunkNumber < 1 || totalChunks < 1
                || chunkNumber > totalChunks || filename == null || filename.trim().isEmpty()) {
            return invalidUploadRequest();
        }

        String path = courseDetailService.saveChunk(chunk, chunkNumber, totalChunks,identifier);
        return ResponseEntity.ok().body(ImmutableMap.of(
                "code", 200,
                "message", "分片上传成功",
                "path", path
        ));
    }

    /**
     * 合并分片接口
     */
    @RequireRoles({0, 1})
    @PostMapping("/mergeChunks")
    public ResponseEntity<?> mergeChunks(
            @RequestParam("filename") String filename,
            @RequestParam("identifier") String identifier) throws IOException {

        if (!validIdentifier(identifier) || filename == null || filename.trim().isEmpty()) {
            return invalidUploadRequest();
        }

        String filePath = courseDetailService.mergeChunks(filename, identifier);
        return ResponseEntity.ok().body(ImmutableMap.of(
                "code", 200,
                "message", "文件合并成功",
                "path", filePath
        ));
    }

    @RequireRoles({0, 1})
    @PostMapping("/checkFileExist")
    public ResponseEntity<?> checkFileExist(
            @RequestParam("identifier") String fileMd5) {

        if (!validIdentifier(fileMd5)) {
            return invalidUploadRequest();
        }

        Map<String, Object> result = courseDetailService.checkFileExist(fileMd5);

        if (Boolean.TRUE.equals(result.get("exist"))) {
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "exist", true,
                    "path", result.get("path"),
                    "message", "文件已存在"
            ));
        } else {
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "exist", false,
                    "uploadedChunks", result.getOrDefault("uploadedChunks", 0),
                    "message", "文件不存在"
            ));
        }
    }

    @PublicEndpoint
    @RequestMapping("/allCourse")
    public ResponseEntity<?> getAllCourse(){
        List<CourseEntity> allCourses = courseService.getAllCourses();
        if(allCourses!=null){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "查询成功",
                    "content", allCourses
            ));
        }else{
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 201,
                    "message", "查询失败"
            ));
        }
    }

    @RequireRoles({0, 1})
    @RequestMapping("/deleteCourse")
    public ResponseEntity<?> deleteCourse(int id){


        int i = courseDetailService.deleteCourseById(id);
        if(i>0){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "success", true,
                    "message", "删除成功"
            ));
        }else{
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "success", false,
                    "message", "删除失败"
            ));
        }
    }

    private UserEntity currentUser(HttpServletRequest request) {
        return (UserEntity) request.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE);
    }

    private String authorName(UserEntity user) {
        if (user.getTeamname() != null && !user.getTeamname().trim().isEmpty()) {
            return user.getTeamname();
        }
        if (user.getRealname() != null && !user.getRealname().trim().isEmpty()) {
            return user.getRealname();
        }
        return user.getUsername();
    }

    private boolean validIdentifier(String identifier) {
        return identifier != null && FILE_IDENTIFIER.matcher(identifier).matches();
    }

    private ResponseEntity<?> invalidUploadRequest() {
        return ResponseEntity.badRequest().body(ImmutableMap.of(
                "code", 400,
                "message", "上传参数不合法"
        ));
    }
}
