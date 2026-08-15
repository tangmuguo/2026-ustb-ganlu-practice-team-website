package com.vihu.ganlu.actions;

import com.google.common.collect.ImmutableMap;
import com.vihu.ganlu.entitys.TeamEntity;
import com.vihu.ganlu.entitys.FileDeletionTaskEntity;
import com.vihu.ganlu.entitys.TeamMediaEntity;
import com.vihu.ganlu.entitys.PublicImageUploadInfo;
import com.vihu.ganlu.entitys.PublicImageMigrationReport;
import com.vihu.ganlu.entitys.TeamPageImageEntity;
import com.vihu.ganlu.entitys.TeamPageWordEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.mappers.TeamMapper;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.PublicEndpoint;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.security.TokenService;
import com.vihu.ganlu.service.TeamMediaService;
import com.vihu.ganlu.service.TeamPageImageService;
import com.vihu.ganlu.service.TeamPageWordService;
import com.vihu.ganlu.service.UserService;
import com.vihu.ganlu.service.impl.FileDeletionTaskService;
import com.vihu.ganlu.service.impl.PublicImageMigrationService;
import com.vihu.ganlu.utils.FileStorageUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    @Resource
    private TokenService tokenService;
    @Resource
    private UserService userService;
    @Resource
    private FileDeletionTaskService fileDeletionTaskService;
    @Resource
    private PublicImageMigrationService publicImageMigrationService;

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
    @PostMapping("/team-content/images/stage")
    public ResponseEntity<?> stageImage(
            @RequestParam("imageFile") MultipartFile imageFile,
            HttpServletRequest request) {
        PublicImageUploadInfo staged = teamPageImageService.stageTeamImage(
                imageFile, currentUser(request).getId());
        return ok("图片已暂存，请完成内容保存", staged);
    }

    @RequireRoles({0, 1})
    @DeleteMapping("/team-content/images/stage")
    public ResponseEntity<?> cancelStagedImage(
            @RequestParam("token") String token,
            HttpServletRequest request) {
        teamPageImageService.cancelStagedTeamImage(token, currentUser(request).getId());
        return ok("临时图片已清理");
    }

    @RequireRoles({0, 1})
    @PostMapping(value = "/team-content/members", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadMember(@RequestParam("file") MultipartFile file,
                                          @RequestParam(value = "caption", required = false) String caption,
                                          @RequestParam(value = "content", required = false) String content,
                                          @RequestParam(value = "logDate", required = false) Date logDate,
                                          HttpServletRequest request) {
        return uploadImage(file, caption, content, logDate,
                TeamPageImageEntity.TYPE_MEMBER_PHOTO, request);
    }

    @RequireRoles({0, 1})
    @PostMapping(value = "/team-content/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadPhoto(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "caption", required = false) String caption,
                                         @RequestParam(value = "content", required = false) String content,
                                         @RequestParam(value = "logDate", required = false) Date logDate,
                                         @RequestParam(value = "type", defaultValue = "2") int type,
                                         HttpServletRequest request) {
        if (!isTeachingStylePhotoType(type)) {
            return badRequest("无效的照片类型: " + type + "（仅支持 2=支教风采）");
        }
        return uploadImage(file, caption, content, logDate, type, request);
    }

    @RequireRoles({0, 1})
    @PostMapping(value = "/team-content/members", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> saveStagedMember(
            @RequestBody TeamPageImageEntity image,
            HttpServletRequest request) {
        return saveStagedImage(image, TeamPageImageEntity.TYPE_MEMBER_PHOTO, request);
    }

    @RequireRoles({0, 1})
    @PostMapping(value = "/team-content/photos", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> saveStagedPhoto(
            @RequestBody TeamPageImageEntity image,
            HttpServletRequest request) {
        int requestedType = image == null || image.getType() == null
                ? TeamPageImageEntity.TYPE_TEACHING_STYLE_PHOTO : image.getType();
        if (!isTeachingStylePhotoType(requestedType)) {
            return badRequest("无效的照片类型: " + requestedType + "（仅支持 2=支教风采）");
        }
        return saveStagedImage(image, TeamPageImageEntity.TYPE_TEACHING_STYLE_PHOTO, request);
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

        TeamMediaEntity media;
        try {
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
        // 团队账号（level=1）必须绑定小队；未绑定时 resolveTeamId 返回 null，
        // 直接拒绝，避免后续 archiveByIdAndTeamId(id, teamId) 对 int 形参自动拆箱触发 NPE → 500。
        // 管理员（level=0）走 archiveById 分支，不依赖 teamId，无需此守卫。
        if (u.getLevel() != 0 && teamId == null) {
            return badRequest("当前用户未绑定小队");
        }
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

        // Mapper 层已按扫描状态筛选；这里再次收口，避免未来查询实现变化或异常数据
        // 让未完成/失败扫描的历史图片进入公开响应。
        List<TeamPageImageEntity> images = teamPageImageService.findByTeamIdAndStatus(teamId, "PUBLISHED").stream()
                .filter(this::isPubliclyReadableImage)
                .collect(java.util.stream.Collectors.toList());
        List<TeamPageWordEntity> words = teamPageWordService.findByTeamIdAndStatus(teamId, "PUBLISHED");
        // Item 6: 公开端 media 只返回 PUBLISHED 且（无父内容 或 父内容 PUBLISHED 同 team）的记录，
        // 并转 DTO 脱敏（不暴露 relativePath/uploaderId）。
        List<TeamMediaEntity> publicMedia = teamMediaService.findPublicByTeamId(teamId);
        List<com.vihu.ganlu.entitys.TeamMediaPublicDto> mediaDto = publicMedia.stream()
                .map(com.vihu.ganlu.entitys.TeamMediaPublicDto::from)
                .collect(java.util.stream.Collectors.toList());

        Map<String, Object> content = ImmutableMap.of(
                "images", images,
                "words", words,
                "media", mediaDto);
        return ok("查询成功", content);
    }

    @PublicEndpoint
    @GetMapping("/team-content/media/{mediaId}/download")
    public ResponseEntity<?> download(@PathVariable int mediaId) {
        TeamMediaEntity m = teamMediaService.findById(mediaId);
        if (m == null || !"PUBLISHED".equals(m.getStatus()) || !isCleanMedia(m)) {
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
        return buildDownloadResponse(m);
    }

    /**
     * 受控图片读取（inline 渲染，供管理员/团队端 <img> 预览 PENDING/REJECTED 图片）。
     *
     * 解决 Bug 1：Item 4 把 PENDING 图片移到 images_pending/ 后无静态映射，
     * 管理员审核时看不到图片内容。此接口按权限返回图片流：
     * - 匿名/无 token 或 token 无效 → 仅当图片 PUBLISHED 且团队 PUBLISHED 时返回，否则 404
     * - 管理员（level=0）→ 任意状态可看
     * - 团队负责人（level=1）→ 仅自己 teamId 的图片可看（任意状态）
     *
     * 鉴权方式：@PublicEndpoint 保留公开 PUBLISHED 图片能力；私有图片只接受
     * Authorization header，禁止从 URL query 读取登录 JWT，避免凭证进入历史、日志或 Referer。
     * 前端用带 header 的 Blob 请求预览；返回 inline（非 attachment）。
     */
    @PublicEndpoint
    @GetMapping("/team-content/image/{imageId}")
    public ResponseEntity<?> serveImage(@PathVariable int imageId,
                                        HttpServletRequest request) {
        TeamPageImageEntity img = teamPageImageService.findById(imageId);
        if (img == null || img.getTeamId() == null || img.getImageUrl() == null
                || !isCleanImage(img)) {
            return ResponseEntity.status(404).body(ImmutableMap.of("code", 404, "message", "资源不存在"));
        }

        UserEntity u = resolveUserFromAuthorization(request);
        boolean isAdmin = u != null && u.getLevel() != null && u.getLevel() == 0;
        boolean isOwner = false;
        if (u != null && u.getLevel() != null && u.getLevel() == 1) {
            Integer teamId = resolveTeamId(u);
            isOwner = teamId != null && teamId.equals(img.getTeamId());
        }

        // 非管理员、非所属团队负责人 → 视为匿名，仅 PUBLISHED 链路完整时可见
        if (!isAdmin && !isOwner) {
            if (!"PUBLISHED".equals(img.getStatus())) {
                return ResponseEntity.status(404).body(ImmutableMap.of("code", 404, "message", "资源不存在"));
            }
            TeamEntity team = teamMapper.findById(img.getTeamId());
            if (team == null || team.getStatus() != TeamEntity.Status.PUBLISHED) {
                return ResponseEntity.status(404).body(ImmutableMap.of("code", 404, "message", "资源不存在"));
            }
        }

        // exy v5 Item 1 缓存策略：匿名访问 PUBLISHED 图可缓存，缓解全走 serveImage 的性能开销。
        // 管理员/owner 访问 PENDING/REJECTED 图必须 no-store（审核中内容不应进浏览器缓存）。
        // PENDING 图匿名拿不到（上面已 404），此约束天然满足。
        // 诚实声明：状态机可回退（驳回/归档可把 PUBLISHED 撤回），撤回后 1 小时内匿名端可能
        // 命中浏览器缓存的旧图——计划已接受的权衡（内容非敏感）。用 private 限定仅浏览器缓存，
        // 不让共享代理/CDN 缓存撤回内容；如需更严可改为 no-store。
        boolean cacheable = !isAdmin && !isOwner;
        return buildImageResponse(img, cacheable);
    }

    /**
     * 构造图片 inline 响应（Content-Type 由文件扩展名推断，inline 便于浏览器渲染）。
     * @param cacheable true=可缓存（PUBLISHED 图，Cache-Control: private, max-age=3600，
     *                  private 限定仅浏览器缓存、不进共享 CDN，见上方诚实声明）；
     *                  false=no-store（管理员/owner 预览 PENDING/REJECTED 图）
     */
    private ResponseEntity<?> buildImageResponse(TeamPageImageEntity img, boolean cacheable) {
        Path path = fileStorageUtil.loadFile(img.getImageUrl());
        if (!java.nio.file.Files.exists(path)) {
            return ResponseEntity.status(404).body(ImmutableMap.of("code", 404, "message", "文件不存在"));
        }
        org.springframework.core.io.Resource resource = new org.springframework.core.io.FileSystemResource(path.toFile());
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, guessImageContentType(img.getImageUrl()))
                .header(HttpHeaders.CACHE_CONTROL, cacheable ? "private, max-age=3600" : "no-store")
                .header("X-Content-Type-Options", "nosniff");
        return builder.body(resource);
    }

    /**
     * 只从 Authorization header 解析当前用户。查询参数中的 token 永不读取。
     * 解析失败返回 null（调用方按匿名处理）。
     *
     * 本端点 @PublicEndpoint，拦截器通常不预处理，因此这里按同一 Bearer 规则做弱校验；
     * 解析失败按匿名用户处理。
     */
    private UserEntity resolveUserFromAuthorization(HttpServletRequest request) {
        UserEntity attr = (UserEntity) request.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE);
        if (attr != null) {
            return attr;
        }
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        String token = auth.substring("Bearer ".length()).trim();
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            Integer userId = tokenService.verifyAndGetUserId(token);
            return userService.findUserById(userId);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String guessImageContentType(String imageUrl) {
        if (imageUrl == null) return "image/jpeg";
        String lower = imageUrl.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/jpeg"; // jpg/jpeg 默认
    }
    @RequireRoles({0, 1})
    @GetMapping("/team-content/media/{mediaId}/owner-download")
    public ResponseEntity<?> ownerDownload(@PathVariable int mediaId, HttpServletRequest request) {
        UserEntity u = currentUser(request);
        TeamMediaEntity m = teamMediaService.findById(mediaId);
        if (m == null || m.getTeamId() == null) {
            return ResponseEntity.status(404).body(ImmutableMap.of("code", 404, "message", "资源不存在"));
        }
        // 团队账号（level=1）必须是该 media 所属团队的负责人；管理员（level=0）跳过此校验。
        if (u.getLevel() != 0) {
            Integer teamId = resolveTeamId(u);
            if (teamId == null || !teamId.equals(m.getTeamId())) {
                return ResponseEntity.status(403).body(ImmutableMap.of("code", 403, "message", "无访问权限"));
            }
        }
        return buildDownloadResponse(m);
    }

    /**
     * 管理员下载附件（任意状态，用于审核 PENDING/REJECTED 时查看内容）。
     */
    @RequireRoles({0})
    @GetMapping("/admin/team-content/media/{mediaId}/download")
    public ResponseEntity<?> adminDownload(@PathVariable int mediaId) {
        TeamMediaEntity m = teamMediaService.findById(mediaId);
        if (m == null || m.getTeamId() == null) {
            return ResponseEntity.status(404).body(ImmutableMap.of("code", 404, "message", "资源不存在"));
        }
        return buildDownloadResponse(m);
    }

    /**
     * 构造下载响应（Content-Disposition/Content-Type/Content-Length/nosniff）。
     * 抽取自 download()，供公开/团队端/管理员端三个接口复用。
     */
    private ResponseEntity<?> buildDownloadResponse(TeamMediaEntity m) {
        if (m == null || !isCleanMedia(m)) {
            return ResponseEntity.status(404)
                    .body(ImmutableMap.of("code", 404, "message", "资源不存在"));
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
        if (requireTextOrNull(reason) == null) {
            return badRequest("驳回原因不能为空");
        }
        // 与 DB reject_reason varchar(512) 对齐
        if (reason.length() > 512) {
            return badRequest("驳回原因长度不能超过512字符");
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

    @RequireRoles({0})
    @PostMapping("/admin/team-content/image/{id}/purge")
    public ResponseEntity<?> adminPurgeImage(@PathVariable int id) {
        return teamPageImageService.purgeById(id)
                ? ok("图片已进入持久化删除队列")
                : badRequest("图片不存在");
    }

    @RequireRoles({0})
    @PostMapping("/admin/team-content/media/{id}/purge")
    public ResponseEntity<?> adminPurgeMedia(@PathVariable int id) {
        return teamMediaService.purgeById(id)
                ? ok("附件已进入持久化删除队列")
                : badRequest("附件不存在");
    }

    @RequireRoles({0})
    @GetMapping("/admin/file-deletion-tasks")
    public ResponseEntity<?> adminListDeletionTasks(
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        List<FileDeletionTaskEntity> tasks = fileDeletionTaskService.listTasks(limit);
        return ok("查询成功", tasks);
    }

    @RequireRoles({0})
    @PostMapping("/admin/file-deletion-tasks/{id}/retry")
    public ResponseEntity<?> adminRetryDeletionTask(@PathVariable long id) {
        return fileDeletionTaskService.retryNow(id)
                ? ok("删除任务已完成或不存在")
                : badRequest("本次重试仍失败，系统将继续自动重试");
    }

    @RequireRoles({0})
    @GetMapping("/admin/public-image-migration/preflight")
    public ResponseEntity<?> adminPreflightPublicImages() {
        PublicImageMigrationReport report = publicImageMigrationService.preflight();
        return report.isMigrationAllowed()
                ? ok(report.isConsistent() ? "公共图片账本已一致" : "预检通过，可以在维护窗口执行迁移", report)
                : ResponseEntity.status(HttpStatus.CONFLICT).body(ImmutableMap.of(
                        "code", HttpStatus.CONFLICT.value(),
                        "message", "公共图片预检存在阻断项，禁止迁移和发布",
                        "content", report));
    }

    @RequireRoles({0})
    @PostMapping("/admin/public-image-migration/migrate")
    public ResponseEntity<?> adminMigratePublicImages() {
        try {
            return ok("公共图片迁移完成并通过一致性断言", publicImageMigrationService.migrate());
        } catch (PublicImageMigrationService.MigrationBlockedException error) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ImmutableMap.of(
                    "code", HttpStatus.CONFLICT.value(),
                    "message", error.getMessage(),
                    "content", error.getReport()));
        }
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
        // 服务端字段校验（与 DB varchar(255) 对齐）：caption 必填，content 可空
        if (requireTextOrNull(caption) == null) {
            return badRequest("标题不能为空");
        }
        if (!withinLength(caption, 255) || !withinLength(content, 255)) {
            return badRequest("标题或说明长度不能超过255字符");
        }
        PublicImageUploadInfo staged;
        try {
            staged = teamPageImageService.stageTeamImage(file, u.getId());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return badRequest(e.getMessage());
        }
        try {
            TeamPageImageEntity entity = new TeamPageImageEntity();
            entity.setTeamId(teamId);
            entity.setUserId(u.getId());
            entity.setImageUploadUserId(u.getId());
            entity.setImageUploadToken(staged.getToken());
            entity.setCaption(caption);
            entity.setContent(content);
            entity.setLogDate(logDate);
            entity.setType(type);
            entity.setStatus("PENDING");
            entity.setRejectReason(null);
            teamPageImageService.insertTeamImage(entity);
            return ok("上传成功", entity);
        } catch (Exception e) {
            teamPageImageService.cancelStagedTeamImage(staged.getToken(), u.getId());
            return badRequest("上传失败: " + e.getMessage());
        }
    }

    private ResponseEntity<?> saveStagedImage(
            TeamPageImageEntity image, int type, HttpServletRequest request) {
        if (image == null || requireTextOrNull(image.getCaption()) == null) {
            return badRequest("标题不能为空");
        }
        if (!withinLength(image.getCaption(), 255) || !withinLength(image.getContent(), 255)) {
            return badRequest("标题或说明长度不能超过255字符");
        }
        if (requireTextOrNull(image.getImageUploadToken()) == null) {
            return badRequest("请先完成图片暂存上传");
        }
        UserEntity user = currentUser(request);
        Integer teamId = resolveTeamId(user);
        if (teamId == null) return badRequest("当前用户未绑定小队");

        image.setTeamId(teamId);
        image.setUserId(user.getId());
        image.setImageUploadUserId(user.getId());
        image.setType(type);
        image.setStatus("PENDING");
        image.setRejectReason(null);
        try {
            teamPageImageService.insertTeamImage(image);
            return ok("上传成功", image);
        } catch (IllegalArgumentException | IllegalStateException error) {
            return badRequest(error.getMessage());
        }
    }

    private boolean isTeachingStylePhotoType(int type) {
        return type == TeamPageImageEntity.TYPE_TEACHING_STYLE_PHOTO;
    }

    private ResponseEntity<?> uploadWord(String caption, String content, Date logDate,
                                         int type, HttpServletRequest request) {
        UserEntity u = currentUser(request);
        Integer teamId = resolveTeamId(u);
        if (teamId == null) {
            return badRequest("当前用户未绑定小队");
        }
        // 服务端字段校验（与 DB varchar(255) NOT NULL 对齐）：caption/content 均必填
        if (requireTextOrNull(caption) == null) {
            return badRequest("标题不能为空");
        }
        if (requireTextOrNull(content) == null) {
            return badRequest("内容不能为空");
        }
        if (!withinLength(caption, 255) || !withinLength(content, 255)) {
            return badRequest("标题或内容长度不能超过255字符");
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

    private boolean isPubliclyReadableImage(TeamPageImageEntity image) {
        return image != null && "PUBLISHED".equals(image.getStatus()) && isCleanImage(image);
    }

    private boolean isCleanImage(TeamPageImageEntity image) {
        return image != null
                && "CLEAN".equals(image.getScanStatus())
                && "CLEAN".equals(image.getScanDiagnosticStatus());
    }

    private boolean isCleanMedia(TeamMediaEntity media) {
        return media != null
                && "CLEAN".equals(media.getScanStatus())
                && "CLEAN".equals(media.getScanDiagnosticStatus());
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

    /**
     * F8 review: service 层抛 IllegalStateException（如 F1 文件搬运失败、DB 同步失败）
     * 在本控制器兜底为结构化 500，而非 Spring 默认空响应——管理员能得到可操作的错误提示。
     * （之前这类异常会冒泡成裸 500，管理员看不到原因。）
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ImmutableMap.of("code", 500, "message", "操作失败：" + e.getMessage()));
    }

    /**
     * 必填文本校验：trim 后为空返回 null（表示校验失败，调用方应返回 400）。
     * @return 校验通过返回 trim 后的值；空返回 null
     */
    private String requireTextOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 校验文本长度不超过 max（不 trim null，空值视为合法由调用方决定）。
     * @return 超长返回 false
     */
    private boolean withinLength(String value, int max) {
        return value == null || value.length() <= max;
    }
}
