package com.vihu.ganlu.actions;

import com.google.common.collect.ImmutableMap;
import com.vihu.ganlu.entitys.TeamEntity;
import com.vihu.ganlu.entitys.TeamMediaEntity;
import com.vihu.ganlu.entitys.TeamPageImageEntity;
import com.vihu.ganlu.entitys.TeamPageWordEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.mappers.TeamMapper;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.PublicEndpoint;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.service.TeamMediaService;
import com.vihu.ganlu.service.TeamPageImageService;
import com.vihu.ganlu.service.TeamPageWordService;
import com.vihu.ganlu.utils.FileStorageUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Date;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
public class TeamContentAction {

    @Resource
    private TeamPageImageService teamPageImageService;
    @Resource
    private TeamPageWordService teamPageWordService;
    @Resource
    private TeamMediaService teamMediaService;
    @Resource
    private FileStorageUtil fileStorageUtil;
    @Resource
    private TeamMapper teamMapper;

    // =====================================================================
    // 团队端 @RequireRoles({0,1}) — teamId 从 Token 推导，不信任任何客户端输入
    // =====================================================================

    @RequireRoles({0, 1})
    @GetMapping("/team-content/mine")
    public ResponseEntity<?> getMyTeamContent(HttpServletRequest request) {
        UserEntity u = currentUser(request);
        Integer teamId = resolveTeamId(u);
        if (teamId == null) {
            return badRequest("当前用户未绑定小队");
        }

        List<TeamPageImageEntity> images = teamPageImageService.findByTeamId(teamId);
        List<TeamPageWordEntity> words = teamPageWordService.findByTeamId(teamId);
        List<TeamMediaEntity> media = teamMediaService.findByTeamId(teamId);

        Map<String, Object> content = ImmutableMap.of(
                "images", images,
                "words", words,
                "media", media);
        return ok("查询成功", content);
    }

    @RequireRoles({0, 1})
    @PostMapping("/team-content/members")
    public ResponseEntity<?> uploadMember(@RequestParam("file") MultipartFile file,
                                          @RequestParam(value = "caption", required = false) String caption,
                                          @RequestParam(value = "content", required = false) String content,
                                          @RequestParam(value = "logDate", required = false) Date logDate,
                                          HttpServletRequest request) {
        return uploadImage(file, caption, content, logDate, 1, request);
    }

    @RequireRoles({0, 1})
    @PostMapping("/team-content/photos")
    public ResponseEntity<?> uploadPhoto(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "caption", required = false) String caption,
                                         @RequestParam(value = "content", required = false) String content,
                                         @RequestParam(value = "logDate", required = false) Date logDate,
                                         @RequestParam(value = "type", defaultValue = "2") int type,
                                         HttpServletRequest request) {
        if (type != 2 && type != 3) {
            return badRequest("无效的图片类型: " + type + "（仅支持 2=支教照片, 3=地区照片）");
        }
        return uploadImage(file, caption, content, logDate, type, request);
    }

    @RequireRoles({0, 1})
    @PostMapping("/team-content/logs")
    public ResponseEntity<?> uploadLog(@RequestParam(value = "caption") String caption,
                                       @RequestParam(value = "content") String content,
                                       @RequestParam(value = "logDate", required = false) Date logDate,
                                       HttpServletRequest request) {
        return uploadWord(caption, content, logDate, 4, request);
    }

    @RequireRoles({0, 1})
    @PostMapping("/team-content/honors")
    public ResponseEntity<?> uploadHonor(@RequestParam(value = "caption") String caption,
                                         @RequestParam(value = "content") String content,
                                         @RequestParam(value = "logDate", required = false) Date logDate,
                                         HttpServletRequest request) {
        return uploadWord(caption, content, logDate, 3, request);
    }

    @RequireRoles({0, 1})
    @PostMapping("/team-content/media")
    public ResponseEntity<?> uploadMedia(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "relatedType", required = false) String relatedType,
                                         @RequestParam(value = "relatedId", required = false) Integer relatedId,
                                         HttpServletRequest request) {
        UserEntity u = currentUser(request);
        Integer teamId = resolveTeamId(u);
        if (teamId == null) {
            return badRequest("当前用户未绑定小队");
        }

        // 校验父内容归属：附件只能关联到当前团队的内容。
        // relatedType 与 relatedId 必须同时为空或同时非空，避免产生半关联记录。
        if ((relatedType == null) != (relatedId == null)) {
            return badRequest("relatedType 与 relatedId 必须同时提供或同时省略");
        }
        if (relatedType != null && relatedId != null) {
            if (!"IMAGE".equals(relatedType) && !"WORD".equals(relatedType)) {
                return badRequest("无效的关联类型: " + relatedType);
            }
            if (!parentExistsAndBelongsToTeam(relatedType, relatedId, teamId)) {
                return badRequest("关联的父内容不存在或不属于当前团队");
            }
        }

        TeamMediaEntity media;
        try {
            if (file.getSize() > FileStorageUtil.MAX_VIDEO_SIZE) {
                return badRequest("文件大小超过限制");
            }
            // 服务端文件类型校验（扩展名 + 魔数，防伪装）
            String ext = fileStorageUtil.extractExtension(file.getOriginalFilename());
            if (FileStorageUtil.VIDEO_EXT.contains(ext)) {
                fileStorageUtil.isAllowedVideo(file);
            } else if (FileStorageUtil.DOC_EXT.contains(ext)) {
                fileStorageUtil.isAllowedDocument(file);
            } else {
                return badRequest("不支持的文件类型: " + ext);
            }
            media = teamMediaService.uploadMedia(file, u.getId(), teamId, relatedType, relatedId);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            return badRequest("上传失败: " + e.getMessage());
        }
        return ok("上传成功", media);
    }

    @RequireRoles({0, 1})
    @PostMapping("/team-content/{type}/{id}/delete")
    public ResponseEntity<?> delete(@PathVariable String type, @PathVariable int id,
                                    HttpServletRequest request) {
        UserEntity u = currentUser(request);
        Integer teamId = resolveTeamId(u);
        boolean ok;
        switch (type) {
            case "image":
                ok = (u.getLevel() == 0)
                        ? teamPageImageService.archiveById(id)
                        : teamPageImageService.archiveByIdAndTeamId(id, teamId);
                break;
            case "word":
                ok = (u.getLevel() == 0)
                        ? teamPageWordService.archiveById(id)
                        : teamPageWordService.archiveByIdAndTeamId(id, teamId);
                break;
            case "media":
                ok = (u.getLevel() == 0)
                        ? teamMediaService.updateStatus(id, "ARCHIVED", null)
                        : teamMediaService.archiveByIdAndTeamId(id, teamId);
                break;
            default:
                return badRequest("未知类型: " + type);
        }
        return ok ? ok("删除成功") : badRequest("删除失败或无权操作");
    }

    // =====================================================================
    // 公开端 @PublicEndpoint
    // =====================================================================

    @PublicEndpoint
    @GetMapping("/team-content/public/{teamId}")
    public ResponseEntity<?> getPublicTeamContent(@PathVariable int teamId) {
        // 校验团队存在且状态为 PUBLISHED（公开端只暴露已发布团队）
        TeamEntity team = teamMapper.findById(teamId);
        if (team == null || team.getStatus() != TeamEntity.Status.PUBLISHED) {
            // 团队不存在或已归档/未发布，返回空结果（不暴露团队是否存在）
            Map<String, Object> content = ImmutableMap.of(
                    "images", java.util.Collections.emptyList(),
                    "words", java.util.Collections.emptyList(),
                    "media", java.util.Collections.emptyList());
            return ok("查询成功", content);
        }

        List<TeamPageImageEntity> images = teamPageImageService.findByTeamIdAndStatus(teamId, "PUBLISHED");
        List<TeamPageWordEntity> words = teamPageWordService.findByTeamIdAndStatus(teamId, "PUBLISHED");
        List<TeamMediaEntity> media = teamMediaService.findByStatus(teamId, "PUBLISHED");

        Map<String, Object> content = ImmutableMap.of(
                "images", images,
                "words", words,
                "media", media);
        return ok("查询成功", content);
    }

    @PublicEndpoint
    @GetMapping("/team-content/media/{mediaId}/download")
    public ResponseEntity<?> download(@PathVariable int mediaId) {
        TeamMediaEntity m = teamMediaService.findById(mediaId);
        if (m == null || !"PUBLISHED".equals(m.getStatus())) {
            return ResponseEntity.status(404).body(ImmutableMap.of("code", 404, "message", "资源不存在"));
        }
        // media 必须归属某个团队；team_id 为空视为非法记录，直接 404
        if (m.getTeamId() == null) {
            return ResponseEntity.status(404).body(ImmutableMap.of("code", 404, "message", "资源不存在"));
        }
        // 校验所属团队存在且状态为 PUBLISHED
        TeamEntity team = teamMapper.findById(m.getTeamId());
        if (team == null || team.getStatus() != TeamEntity.Status.PUBLISHED) {
            return ResponseEntity.status(404).body(ImmutableMap.of("code", 404, "message", "资源不存在"));
        }
        // 级联检查：如果 media 关联了父内容，父内容也必须 PUBLISHED 且属于同一 team
        if (m.getRelatedType() != null && m.getRelatedId() != null) {
            boolean parentPublished = isParentPublishedAndBelongsToTeam(m.getRelatedType(), m.getRelatedId(), m.getTeamId());
            if (!parentPublished) {
                return ResponseEntity.status(404).body(ImmutableMap.of("code", 404, "message", "资源不存在"));
            }
        }
        Path path = fileStorageUtil.loadFile(m.getRelativePath());
        org.springframework.core.io.Resource resource = new org.springframework.core.io.FileSystemResource(path.toFile());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, m.getMimeType() != null ? m.getMimeType() : "application/octet-stream")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodeFilename(m.getFilename()) + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(m.getFileSize()))
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    // =====================================================================
    // 管理员端 @RequireRoles({0})
    // =====================================================================

    /**
     * 管理员获取所有团队列表（用于下拉选择）。
     */
    @RequireRoles({0})
    @GetMapping("/admin/team-content/teams")
    public ResponseEntity<?> adminListTeams() {
        List<TeamEntity> teams = teamMapper.findAllTeams();
        return ok("查询成功", teams);
    }

    @RequireRoles({0})
    @GetMapping("/admin/team-content")
    public ResponseEntity<?> adminListContent(@RequestParam(value = "teamId", required = false) Integer teamId,
                                              @RequestParam(value = "status", required = false) String status) {
        List<TeamPageImageEntity> images;
        List<TeamPageWordEntity> words;
        List<TeamMediaEntity> media;
        if (teamId != null) {
            images = (status != null && !status.isEmpty())
                    ? teamPageImageService.findByTeamIdAndStatus(teamId, status)
                    : teamPageImageService.findByTeamId(teamId);
            words = (status != null && !status.isEmpty())
                    ? teamPageWordService.findByTeamIdAndStatus(teamId, status)
                    : teamPageWordService.findByTeamId(teamId);
            media = (status != null && !status.isEmpty())
                    ? teamMediaService.findByStatus(teamId, status)
                    : teamMediaService.findByTeamId(teamId);
        } else {
            // 未指定 teamId 时返回空（管理员必须指定团队）
            images = java.util.Collections.emptyList();
            words = java.util.Collections.emptyList();
            media = java.util.Collections.emptyList();
        }
        Map<String, Object> content = ImmutableMap.of(
                "images", images,
                "words", words,
                "media", media);
        return ok("查询成功", content);
    }

    @RequireRoles({0})
    @PostMapping("/admin/team-content/{type}/{id}/publish")
    public ResponseEntity<?> adminPublish(@PathVariable String type, @PathVariable int id) {
        boolean ok;
        switch (type) {
            case "image":
                ok = teamPageImageService.updateStatus(id, "PUBLISHED", null);
                break;
            case "word":
                ok = teamPageWordService.updateStatus(id, "PUBLISHED", null);
                break;
            case "media":
                ok = teamMediaService.updateStatus(id, "PUBLISHED", null);
                break;
            default:
                return badRequest("未知类型: " + type);
        }
        return ok ? ok("发布成功") : badRequest("操作失败");
    }

    @RequireRoles({0})
    @PostMapping("/admin/team-content/{type}/{id}/reject")
    public ResponseEntity<?> adminReject(@PathVariable String type, @PathVariable int id,
                                         @RequestParam("reason") String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return badRequest("驳回原因不能为空");
        }
        boolean ok;
        switch (type) {
            case "image":
                ok = teamPageImageService.updateStatus(id, "REJECTED", reason);
                break;
            case "word":
                ok = teamPageWordService.updateStatus(id, "REJECTED", reason);
                break;
            case "media":
                ok = teamMediaService.updateStatus(id, "REJECTED", reason);
                break;
            default:
                return badRequest("未知类型: " + type);
        }
        return ok ? ok("驳回成功") : badRequest("操作失败");
    }

    @RequireRoles({0})
    @PostMapping("/admin/team-content/{type}/{id}/archive")
    public ResponseEntity<?> adminArchive(@PathVariable String type, @PathVariable int id) {
        boolean ok;
        switch (type) {
            case "image":
                ok = teamPageImageService.updateStatus(id, "ARCHIVED", null);
                break;
            case "word":
                ok = teamPageWordService.updateStatus(id, "ARCHIVED", null);
                break;
            case "media":
                ok = teamMediaService.updateStatus(id, "ARCHIVED", null);
                break;
            default:
                return badRequest("未知类型: " + type);
        }
        return ok ? ok("归档成功") : badRequest("操作失败");
    }

    // =====================================================================
    // 私有辅助方法
    // =====================================================================

    private ResponseEntity<?> uploadImage(MultipartFile file, String caption, String content,
                                         Date logDate, int type, HttpServletRequest request) {
        UserEntity u = currentUser(request);
        Integer teamId = resolveTeamId(u);
        if (teamId == null) {
            return badRequest("当前用户未绑定小队");
        }
        try {
            fileStorageUtil.isAllowedImage(file);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
        String imagePath = teamPageImageService.uploadTeamImage(file);
        try {
            TeamPageImageEntity entity = new TeamPageImageEntity();
            entity.setTeamId(teamId);
            entity.setUserId(u.getId());
            entity.setImageUrl(imagePath);
            entity.setCaption(caption);
            entity.setContent(content);
            entity.setLogDate(logDate);
            entity.setType(type);
            entity.setStatus("PENDING");
            entity.setRejectReason(null);
            teamPageImageService.insertTeamImage(entity);
            return ok("上传成功", entity);
        } catch (Exception e) {
            fileStorageUtil.deleteFile(imagePath); // DB 失败清理孤立文件
            return badRequest("上传失败: " + e.getMessage());
        }
    }

    private ResponseEntity<?> uploadWord(String caption, String content, Date logDate,
                                         int type, HttpServletRequest request) {
        UserEntity u = currentUser(request);
        Integer teamId = resolveTeamId(u);
        if (teamId == null) {
            return badRequest("当前用户未绑定小队");
        }
        TeamPageWordEntity entity = new TeamPageWordEntity();
        entity.setTeamId(teamId);
        entity.setUserId(u.getId());
        entity.setCaption(caption);
        entity.setContent(content);
        entity.setLogDate(logDate);
        entity.setType(type);
        entity.setStatus("PENDING");
        entity.setRejectReason(null);
        int i = teamPageWordService.insertTeamWord(entity);
        if (i > 0) {
            return ok("上传成功", entity);
        }
        return badRequest("上传失败");
    }

    /**
     * 校验父内容存在且属于指定 team（用于上传附件的归属校验）。
     */
    private boolean parentExistsAndBelongsToTeam(String relatedType, int relatedId, int teamId) {
        if ("IMAGE".equals(relatedType)) {
            TeamPageImageEntity e = teamPageImageService.findById(relatedId);
            return e != null && Integer.valueOf(teamId).equals(e.getTeamId())
                    && !"ARCHIVED".equals(e.getStatus());
        } else if ("WORD".equals(relatedType)) {
            TeamPageWordEntity e = teamPageWordService.findById(relatedId);
            return e != null && Integer.valueOf(teamId).equals(e.getTeamId())
                    && !"ARCHIVED".equals(e.getStatus());
        }
        return false;
    }

    /**
     * 级联检查父内容是否 PUBLISHED 且属于指定 team。
     */
    private boolean isParentPublishedAndBelongsToTeam(String relatedType, int relatedId, Integer teamId) {
        if ("IMAGE".equals(relatedType)) {
            TeamPageImageEntity e = teamPageImageService.findById(relatedId);
            return e != null && "PUBLISHED".equals(e.getStatus())
                    && (teamId == null || teamId.equals(e.getTeamId()));
        } else if ("WORD".equals(relatedType)) {
            TeamPageWordEntity e = teamPageWordService.findById(relatedId);
            return e != null && "PUBLISHED".equals(e.getStatus())
                    && (teamId == null || teamId.equals(e.getTeamId()));
        }
        return false;
    }

    /**
     * 从 Token 推导当前用户的 teamId。
     * 通过 team.owner_user_id 字段查找当前用户负责的小队。
     * 团队端权限规则：只要团队未归档即可（允许 DRAFT 状态的负责人准备内容），
     * 与公开端 "PUBLISHED 才可见" 的规则区分开。
     *
     * @return teamId，若用户未绑定小队则返回 null
     */
    private Integer resolveTeamId(UserEntity user) {
        TeamEntity team = teamMapper.findOwnedTeamByOwnerUserId(user.getId());
        return team != null ? team.getId() : null;
    }

    private UserEntity currentUser(HttpServletRequest request) {
        return (UserEntity) request.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE);
    }

    private String encodeFilename(String filename) {
        try {
            return URLEncoder.encode(filename, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return filename;
        }
    }

    private ResponseEntity<?> ok(String message) {
        return ResponseEntity.ok(ImmutableMap.of("code", 200, "message", message));
    }

    private ResponseEntity<?> ok(String message, Object content) {
        return ResponseEntity.ok(ImmutableMap.of("code", 200, "message", message, "content", content));
    }

    private ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ImmutableMap.of("code", 400, "message", message));
    }
}
