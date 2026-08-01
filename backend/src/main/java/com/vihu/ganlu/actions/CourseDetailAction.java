package com.vihu.ganlu.actions;

import com.github.pagehelper.PageInfo;
import com.vihu.ganlu.entitys.CourseDetailEntity;
import com.vihu.ganlu.entitys.MaterialCreateRequest;
import com.vihu.ganlu.entitys.MaterialSearchQuery;
import com.vihu.ganlu.entitys.UploadedFileInfo;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.PublicEndpoint;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.service.CourseDetailService;
import com.vihu.ganlu.utils.FileStorageUtil;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/courseDetail")
public class CourseDetailAction {
    private final CourseDetailService courseDetailService;

    public CourseDetailAction(CourseDetailService courseDetailService) {
        this.courseDetailService = courseDetailService;
    }

    @PublicEndpoint
    @GetMapping("/materials")
    public ResponseEntity<?> search(@ModelAttribute MaterialSearchQuery query) {
        PageInfo<CourseDetailEntity> page = courseDetailService.search(query);
        return ResponseEntity.ok(responseBody(200, "查询成功", page));
    }

    @PublicEndpoint
    @GetMapping("/materials/{id}")
    public ResponseEntity<?> detail(@PathVariable("id") int id) {
        CourseDetailEntity material = courseDetailService.getCourseById(id);
        if (material == null) {
            throw new NoSuchElementException("课件不存在");
        }
        return ResponseEntity.ok(responseBody(200, "查询成功", material));
    }

    @RequireRoles({0, 1})
    @PostMapping("/materials")
    public ResponseEntity<?> create(@RequestBody MaterialCreateRequest request, HttpServletRequest servletRequest)
            throws IOException {
        CourseDetailEntity created = courseDetailService.createMaterial(request, currentUser(servletRequest));
        String message = "FAILED".equals(created.getPreviewStatus())
                ? "上传成功，但 PPT 预览转换失败，可登录后下载原文件"
                : "上传成功";
        return ResponseEntity.status(HttpStatus.CREATED).body(responseBody(200, message, created));
    }

    @RequireRoles({0, 1})
    @DeleteMapping("/materials/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") int id) {
        courseDetailService.deleteCourseById(id);
        return ResponseEntity.ok(responseBody(200, "删除成功", null));
    }

    @RequireRoles({0, 1})
    @PostMapping("/uploadChunk")
    public ResponseEntity<?> uploadChunk(
            @RequestParam("file") MultipartFile chunk,
            @RequestParam("chunkNumber") int chunkNumber,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("identifier") String identifier,
            @RequestParam("filename") String filename,
            @RequestParam("expectedSize") long expectedSize,
            @RequestParam("purpose") String purpose,
            HttpServletRequest request) throws IOException {
        int userId = currentUser(request).getId();
        String savedChunk = courseDetailService.saveChunk(
                chunk, chunkNumber, totalChunks, identifier, filename, expectedSize, purpose, userId);
        return ResponseEntity.ok(responseBody(200, "分片上传成功", savedChunk));
    }

    @RequireRoles({0, 1})
    @PostMapping("/mergeChunks")
    public ResponseEntity<?> mergeChunks(
            @RequestParam("filename") String filename,
            @RequestParam("identifier") String identifier,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("expectedSize") long expectedSize,
            @RequestParam("purpose") String purpose,
            HttpServletRequest request) throws IOException {
        int userId = currentUser(request).getId();
        UploadedFileInfo file = courseDetailService.mergeChunks(
                filename, identifier, totalChunks, expectedSize, purpose, userId);
        return ResponseEntity.ok(responseBody(200, "文件合并成功", file));
    }

    @RequireRoles({0, 1})
    @PostMapping("/checkFileExist")
    public ResponseEntity<?> checkFileExist(
            @RequestParam("identifier") String identifier,
            @RequestParam("purpose") String purpose,
            HttpServletRequest request) throws IOException {
        int userId = currentUser(request).getId();
        Map<String, Object> state = courseDetailService.checkFileExist(identifier, purpose, userId);
        return ResponseEntity.ok(responseBody(200, "查询成功", state));
    }

    @RequireRoles({0, 1})
    @DeleteMapping("/uploadSession")
    public ResponseEntity<?> cancelUpload(
            @RequestParam(value = "identifier", required = false) String identifier,
            @RequestParam("purpose") String purpose,
            @RequestParam(value = "token", required = false) String token,
            HttpServletRequest request) throws IOException {
        int userId = currentUser(request).getId();
        courseDetailService.cancelUpload(identifier, purpose, token, userId);
        return ResponseEntity.ok(responseBody(200, "上传临时文件已清理", null));
    }

    @RequireRoles({0, 1, 2})
    @GetMapping("/materials/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable("id") int id) throws IOException {
        Path path = courseDetailService.getPreviewPath(id);
        Resource resource = new UrlResource(path.toUri());
        String extension = FileStorageUtil.extensionOf(path.getFileName().toString());
        MediaType mediaType;
        switch (extension) {
            case "pdf":
                mediaType = MediaType.APPLICATION_PDF;
                break;
            case "jpg":
            case "jpeg":
                mediaType = MediaType.IMAGE_JPEG;
                break;
            case "png":
                mediaType = MediaType.IMAGE_PNG;
                break;
            case "webp":
                mediaType = MediaType.parseMediaType("image/webp");
                break;
            default:
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ContentDisposition disposition = ContentDisposition.inline()
                .filename("preview." + extension, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(Files.size(path))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(resource);
    }

    @RequireRoles({0, 1, 2})
    @GetMapping("/materials/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable("id") int id) throws IOException {
        CourseDetailEntity material = courseDetailService.getCourseById(id);
        if (material == null) {
            throw new NoSuchElementException("课件不存在");
        }
        Path path = courseDetailService.getDownloadPath(id);
        Resource resource = new UrlResource(path.toUri());
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(material.getMimeType());
        } catch (RuntimeException e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        String filename = material.getOriginalFilename() == null
                ? path.getFileName().toString()
                : material.getOriginalFilename();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(Files.size(path))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(resource);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(responseBody(400, exception.getMessage(), null));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<?> handleNotFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(responseBody(404, exception.getMessage(), null));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleConflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(responseBody(409, exception.getMessage(), null));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<?> handleStorageError(IOException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(responseBody(500, "文件处理失败，请稍后重试", null));
    }

    @ExceptionHandler(FileStorageUtil.StorageException.class)
    public ResponseEntity<?> handleStorageRuntimeError(FileStorageUtil.StorageException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(responseBody(500, "文件存储失败，请稍后重试", null));
    }

    private UserEntity currentUser(HttpServletRequest request) {
        return (UserEntity) request.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE);
    }

    private Map<String, Object> responseBody(int code, String message, Object content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("content", content);
        return body;
    }
}
