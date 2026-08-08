package com.vihu.ganlu.actions;

import com.vihu.ganlu.entitys.TeamEntity;
import com.vihu.ganlu.entitys.TeamMediaEntity;
import com.vihu.ganlu.entitys.TeamPageImageEntity;
import com.vihu.ganlu.entitys.PublicImageUploadInfo;
import com.vihu.ganlu.entitys.TeamPageWordEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.mappers.TeamMapper;
import com.vihu.ganlu.service.TeamMediaService;
import com.vihu.ganlu.service.TeamPageImageService;
import com.vihu.ganlu.service.TeamPageWordService;
import com.vihu.ganlu.utils.FileStorageUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * TeamContentAction 单元测试 — JUnit 5 + Mockito（纯单元，不启动 Spring 容器）。
 */
class TeamContentActionTests {
    private TeamContentAction action;
    private TeamPageImageService imageService;
    private TeamPageWordService wordService;
    private TeamMediaService mediaService;
    private FileStorageUtil fileStorageUtil;
    private TeamMapper teamMapper;
    private com.vihu.ganlu.security.TokenService tokenService;
    private com.vihu.ganlu.service.UserService userService;
    private com.vihu.ganlu.service.impl.FileDeletionTaskService deletionTaskService;
    private com.vihu.ganlu.service.impl.PublicImageMigrationService publicImageMigrationService;

    @BeforeEach
    void setUp() {
        imageService = mock(TeamPageImageService.class);
        wordService = mock(TeamPageWordService.class);
        mediaService = mock(TeamMediaService.class);
        fileStorageUtil = mock(FileStorageUtil.class);
        teamMapper = mock(TeamMapper.class);
        tokenService = mock(com.vihu.ganlu.security.TokenService.class);
        userService = mock(com.vihu.ganlu.service.UserService.class);
        deletionTaskService = mock(com.vihu.ganlu.service.impl.FileDeletionTaskService.class);
        publicImageMigrationService = mock(com.vihu.ganlu.service.impl.PublicImageMigrationService.class);
        action = new TeamContentAction();
        // 通过反射注入 @Resource 字段
        inject(action, "teamPageImageService", imageService);
        inject(action, "teamPageWordService", wordService);
        inject(action, "teamMediaService", mediaService);
        inject(action, "fileStorageUtil", fileStorageUtil);
        inject(action, "teamMapper", teamMapper);
        inject(action, "tokenService", tokenService);
        inject(action, "userService", userService);
        inject(action, "fileDeletionTaskService", deletionTaskService);
        inject(action, "publicImageMigrationService", publicImageMigrationService);
    }

    /**
     * 模拟团队用户：user.id=5 → team.id=10（通过 owner_user_id 绑定）
     */
    private void mockTeamUser(int userId, int teamId) {
        TeamEntity team = new TeamEntity();
        team.setId(teamId);
        when(teamMapper.findOwnedTeamByOwnerUserId(userId)).thenReturn(team);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getMyTeamContent_returnsAllContentForTeam() {
        UserEntity user = user(5, 1);
        mockTeamUser(5, 10); // user 5 → team 10
        when(imageService.findByTeamId(10)).thenReturn(Collections.emptyList());
        when(wordService.findByTeamId(10)).thenReturn(Collections.emptyList());
        when(mediaService.findByTeamId(10)).thenReturn(Collections.emptyList());

        ResponseEntity<?> resp = action.getMyTeamContent(req(user));
        assertEquals(200, resp.getStatusCodeValue());
        verify(imageService).findByTeamId(10); // teamId 从 team.owner_user_id 推导
    }

    @SuppressWarnings("unchecked")
    @Test
    void getMyTeamContent_userWithoutTeam_returns400() {
        UserEntity user = user(5, 1);
        when(teamMapper.findOwnedTeamByOwnerUserId(5)).thenReturn(null);

        ResponseEntity<?> resp = action.getMyTeamContent(req(user));
        assertEquals(400, resp.getStatusCodeValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteImage_level0_canDeleteAny() {
        UserEntity admin = user(1, 0);
        when(imageService.archiveById(10)).thenReturn(true);

        ResponseEntity<?> resp = action.delete("image", 10, req(admin));
        assertEquals(200, resp.getStatusCodeValue());
        verify(imageService).archiveById(10);
    }

    @SuppressWarnings("unchecked")
    @Test
    void deleteImage_level1_canOnlyDeleteOwn() {
        UserEntity team = user(5, 1);
        mockTeamUser(5, 10); // user 5 → team 10
        when(imageService.archiveByIdAndTeamId(10, 10)).thenReturn(true);

        ResponseEntity<?> resp = action.delete("image", 10, req(team));
        assertEquals(200, resp.getStatusCodeValue());
        verify(imageService).archiveByIdAndTeamId(10, 10);
        verify(imageService, never()).archiveById(10);
    }

    @SuppressWarnings("unchecked")
    @Test
    void delete_level1WithoutTeam_returns400_notNpe500() {
        // 回归测试：团队账号未绑定小队时 resolveTeamId 返回 null，
        // delete 必须返回 400，而不是对 archiveByIdAndTeamId(id, null) 自动拆箱触发 NPE → 500
        UserEntity team = user(5, 1);
        when(teamMapper.findOwnedTeamByOwnerUserId(5)).thenReturn(null);

        ResponseEntity<?> resp = action.delete("image", 10, req(team));
        assertEquals(400, resp.getStatusCodeValue());
        verify(imageService, never()).archiveByIdAndTeamId(anyInt(), anyInt());
    }

    @SuppressWarnings("unchecked")
    @Test
    void adminPublish_image_success() {
        when(imageService.updateStatus(10, "PUBLISHED", null)).thenReturn(true);

        ResponseEntity<?> resp = action.adminPublish("image", 10);
        assertEquals(200, resp.getStatusCodeValue());
    }

    @Test
    void adminPublish_moveFailure_returnsStructured500() {
        // F8 review: service 层 move 失败抛 IllegalStateException，本控制器应有 handler 兜底，
        // 返回结构化 ResultEntity（而非 Spring 默认裸 500 空响应）
        when(imageService.updateStatus(10, "PUBLISHED", null))
                .thenThrow(new IllegalStateException("图片文件搬运失败: from -> to"));

        ResponseEntity<?> resp = action.handleIllegalState(
                new IllegalStateException("图片文件搬运失败: from -> to"));
        assertEquals(500, resp.getStatusCodeValue());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> body = (java.util.Map<String, Object>) resp.getBody();
        assertEquals(500, body.get("code"));
        assertEquals("操作失败：图片文件搬运失败: from -> to", body.get("message"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void adminReject_emptyReason_returns400() {
        ResponseEntity<?> resp = action.adminReject("image", 10, "");
        assertEquals(400, resp.getStatusCodeValue());
        verify(imageService, never()).updateStatus(anyInt(), anyString(), anyString());
    }

    @SuppressWarnings("unchecked")
    @Test
    void adminReject_withReason_success() {
        when(imageService.updateStatus(10, "REJECTED", "内容不当")).thenReturn(true);

        ResponseEntity<?> resp = action.adminReject("image", 10, "内容不当");
        assertEquals(200, resp.getStatusCodeValue());
        verify(imageService).updateStatus(10, "REJECTED", "内容不当");
    }

    // =====================================================================
    // Item 11: 服务端字段长度/非空校验（与 DB varchar 限制对齐）
    // =====================================================================

    @SuppressWarnings("unchecked")
    @Test
    void adminReject_reasonTooLong_returns400() {
        // reject_reason varchar(512)
        String tooLong = new String(new char[513]).replace('\0', 'a');
        ResponseEntity<?> resp = action.adminReject("image", 10, tooLong);
        assertEquals(400, resp.getStatusCodeValue());
        verify(imageService, never()).updateStatus(anyInt(), anyString(), anyString());
    }

    @SuppressWarnings("unchecked")
    @Test
    void uploadImage_blankCaption_returns400() {
        UserEntity user = user(5, 1);
        mockTeamUser(5, 10);
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0});

        ResponseEntity<?> resp = action.uploadMember(file, "   ", "content", null, req(user));
        assertEquals(400, resp.getStatusCodeValue());
        verify(imageService, never()).insertTeamImage(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void uploadImage_captionTooLong_returns400() {
        UserEntity user = user(5, 1);
        mockTeamUser(5, 10);
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0});
        String tooLong = new String(new char[256]).replace('\0', 'a');

        ResponseEntity<?> resp = action.uploadMember(file, tooLong, "content", null, req(user));
        assertEquals(400, resp.getStatusCodeValue());
        verify(imageService, never()).insertTeamImage(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void uploadLog_blankContent_returns400() {
        UserEntity user = user(5, 1);
        mockTeamUser(5, 10);

        ResponseEntity<?> resp = action.uploadLog("标题", "   ", null, req(user));
        assertEquals(400, resp.getStatusCodeValue());
        verify(wordService, never()).insertTeamWord(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void uploadLog_contentTooLong_returns400() {
        UserEntity user = user(5, 1);
        mockTeamUser(5, 10);
        String tooLong = new String(new char[256]).replace('\0', 'a');

        ResponseEntity<?> resp = action.uploadLog("标题", tooLong, null, req(user));
        assertEquals(400, resp.getStatusCodeValue());
        verify(wordService, never()).insertTeamWord(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getPublicTeamContent_onlyPublished() {
        TeamEntity team = new TeamEntity();
        team.setId(5);
        team.setStatus(TeamEntity.Status.PUBLISHED);
        when(teamMapper.findById(5)).thenReturn(team);
        when(imageService.findByTeamIdAndStatus(5, "PUBLISHED")).thenReturn(Collections.emptyList());
        when(wordService.findByTeamIdAndStatus(5, "PUBLISHED")).thenReturn(Collections.emptyList());
        // Item 6: 公开端 media 改用 findPublicByTeamId（过滤父内容状态）
        when(mediaService.findPublicByTeamId(5)).thenReturn(Collections.emptyList());

        ResponseEntity<?> resp = action.getPublicTeamContent(5);
        assertEquals(200, resp.getStatusCodeValue());
        verify(imageService).findByTeamIdAndStatus(5, "PUBLISHED");
        verify(mediaService).findPublicByTeamId(5);
    }

    @Test
    void getPublicTeamContent_mediaFilteredByParentStatus() {
        // Item 6: 公开端只返回父内容也是 PUBLISHED 的附件（过滤逻辑在 mapper SQL）
        // 这里验证 service 调用 findPublicByTeamId 而非 findByStatus
        TeamEntity team = new TeamEntity();
        team.setId(5);
        team.setStatus(TeamEntity.Status.PUBLISHED);
        when(teamMapper.findById(5)).thenReturn(team);
        when(imageService.findByTeamIdAndStatus(5, "PUBLISHED")).thenReturn(Collections.emptyList());
        when(wordService.findByTeamIdAndStatus(5, "PUBLISHED")).thenReturn(Collections.emptyList());
        TeamMediaEntity m = new TeamMediaEntity();
        m.setId(1); m.setStatus("PUBLISHED"); m.setTeamId(5); m.setFilename("a.mp4");
        when(mediaService.findPublicByTeamId(5)).thenReturn(java.util.Collections.singletonList(m));

        ResponseEntity<?> resp = action.getPublicTeamContent(5);
        assertEquals(200, resp.getStatusCodeValue());
        verify(mediaService).findPublicByTeamId(5);
        verify(mediaService, never()).findByStatus(anyInt(), anyString());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getPublicTeamContent_archivedTeam_returnsEmpty() {
        // 归档团队 → 公开页返回空结果，不调用子内容查询
        TeamEntity team = new TeamEntity();
        team.setId(5);
        team.setStatus(TeamEntity.Status.ARCHIVED);
        when(teamMapper.findById(5)).thenReturn(team);

        ResponseEntity<?> resp = action.getPublicTeamContent(5);
        assertEquals(200, resp.getStatusCodeValue());
        // 不应查询子内容
        verify(imageService, never()).findByTeamIdAndStatus(anyInt(), anyString());
        verify(wordService, never()).findByTeamIdAndStatus(anyInt(), anyString());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getPublicTeamContent_teamNotFound_returnsEmpty() {
        when(teamMapper.findById(999)).thenReturn(null);

        ResponseEntity<?> resp = action.getPublicTeamContent(999);
        assertEquals(200, resp.getStatusCodeValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    void download_mediaNotFound_returns404() {
        when(mediaService.findById(999)).thenReturn(null);

        ResponseEntity<?> resp = action.download(999);
        assertEquals(404, resp.getStatusCodeValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    void download_mediaNotPublished_returns404() {
        TeamMediaEntity m = new TeamMediaEntity();
        m.setId(10);
        m.setStatus("PENDING");
        when(mediaService.findById(10)).thenReturn(m);

        ResponseEntity<?> resp = action.download(10);
        assertEquals(404, resp.getStatusCodeValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    void download_mediaPublishedButParentRejected_returns404() {
        TeamMediaEntity m = new TeamMediaEntity();
        m.setId(10);
        m.setStatus("PUBLISHED");
        m.setRelatedType("WORD");
        m.setRelatedId(20);
        when(mediaService.findById(10)).thenReturn(m);

        TeamPageWordEntity parent = new TeamPageWordEntity();
        parent.setId(20);
        parent.setStatus("REJECTED");
        when(wordService.findById(20)).thenReturn(parent);

        ResponseEntity<?> resp = action.download(10);
        assertEquals(404, resp.getStatusCodeValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    void uploadImage_emptyFile_returns400() {
        UserEntity user = user(5, 1);
        mockTeamUser(5, 10);
        MockMultipartFile emptyFile = new MockMultipartFile("file", new byte[0]);
        when(imageService.stageTeamImage(emptyFile, 5))
                .thenThrow(new IllegalArgumentException("文件为空"));

        ResponseEntity<?> resp = action.uploadMember(emptyFile, "caption", "content", null, req(user));
        assertEquals(400, resp.getStatusCodeValue());
    }

    @Test
    void adminPurgeMedia_enqueuesDurableDeletion() {
        when(mediaService.purgeById(12)).thenReturn(true);

        ResponseEntity<?> response = action.adminPurgeMedia(12);

        assertEquals(200, response.getStatusCodeValue());
        verify(mediaService).purgeById(12);
    }

    @Test
    void uploadMember_usesLifecycleStageThenBusinessInsert() {
        UserEntity user = user(5, 1);
        mockTeamUser(5, 10);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        when(imageService.stageTeamImage(file, 5)).thenReturn(
                new PublicImageUploadInfo("00000000-0000-0000-0000-000000000001",
                        "a.png", "png", "image/png", file.getSize()));
        when(imageService.insertTeamImage(any(TeamPageImageEntity.class))).thenReturn(1);

        ResponseEntity<?> response = action.uploadMember(file, "队员", "介绍", null, req(user));

        assertEquals(200, response.getStatusCodeValue());
        verify(imageService).stageTeamImage(file, 5);
        verify(imageService).insertTeamImage(argThat(image ->
                Integer.valueOf(10).equals(image.getTeamId())
                        && Integer.valueOf(5).equals(image.getImageUploadUserId())
                        && "PENDING".equals(image.getStatus())));
    }

    @SuppressWarnings("unchecked")
    @Test
    void uploadWord_success() {
        UserEntity user = user(5, 1);
        mockTeamUser(5, 10);
        when(wordService.insertTeamWord(any(TeamPageWordEntity.class))).thenReturn(1);

        ResponseEntity<?> resp = action.uploadLog("标题", "内容", null, req(user));
        assertEquals(200, resp.getStatusCodeValue());
        verify(wordService).insertTeamWord(any(TeamPageWordEntity.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void uploadWord_userWithoutTeam_returns400() {
        UserEntity user = user(5, 1);
        when(teamMapper.findOwnedTeamByOwnerUserId(5)).thenReturn(null);

        ResponseEntity<?> resp = action.uploadLog("标题", "内容", null, req(user));
        assertEquals(400, resp.getStatusCodeValue());
        verify(wordService, never()).insertTeamWord(any(TeamPageWordEntity.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void uploadMedia_invalidRelatedType_returns400() {
        UserEntity user = user(5, 1);
        mockTeamUser(5, 10);
        MockMultipartFile file = new MockMultipartFile("file", "test.mp4",
                "video/mp4", new byte[]{0, 0, 0, 0, 'f', 't', 'y', 'p'});
        when(mediaService.uploadMedia(file, 5, 10, "INVALID", 1))
                .thenThrow(new IllegalArgumentException("无效的关联类型: INVALID"));

        ResponseEntity<?> resp = action.uploadMedia(file, "INVALID", 1, req(user));
        assertEquals(400, resp.getStatusCodeValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    void uploadMedia_parentNotBelongToTeam_returns400() {
        UserEntity user = user(5, 1);
        mockTeamUser(5, 10);
        MockMultipartFile file = new MockMultipartFile("file", "test.doc",
                "application/msword", new byte[]{0, 0, 0, 0, 0, 0, 0, 0});
        when(mediaService.uploadMedia(file, 5, 10, "WORD", 1))
                .thenThrow(new IllegalArgumentException("关联的父内容不存在、不属于当前团队或已归档"));

        ResponseEntity<?> resp = action.uploadMedia(file, "WORD", 1, req(user));
        assertEquals(400, resp.getStatusCodeValue());
        verify(wordService, never()).findById(anyInt());
    }

    @SuppressWarnings("unchecked")
    @Test
    void uploadMedia_onlyRelatedTypeProvided_returns400() {
        // relatedType 与 relatedId 只传一个，应拒绝（避免半关联记录）
        UserEntity user = user(5, 1);
        mockTeamUser(5, 10);
        MockMultipartFile file = new MockMultipartFile("file", "test.mp4",
                "video/mp4", new byte[]{0, 0, 0, 0, 'f', 't', 'y', 'p'});
        when(mediaService.uploadMedia(file, 5, 10, "IMAGE", null))
                .thenThrow(new IllegalArgumentException("relatedType 与 relatedId 必须同时提供或同时省略"));

        ResponseEntity<?> resp = action.uploadMedia(file, "IMAGE", null, req(user));
        assertEquals(400, resp.getStatusCodeValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    void download_mediaWithoutTeamId_returns404() {
        // team_id 为空的 media 视为非法记录，直接 404（不跳过团队校验）
        TeamMediaEntity m = new TeamMediaEntity();
        m.setId(10);
        m.setStatus("PUBLISHED");
        m.setTeamId(null); // 无团队归属
        when(mediaService.findById(10)).thenReturn(m);

        ResponseEntity<?> resp = action.download(10);
        assertEquals(404, resp.getStatusCodeValue());
        verify(teamMapper, never()).findById(anyInt());
    }

    // =====================================================================
    // Item 3: 团队端/管理员端私有下载（可下 PENDING/REJECTED 附件）
    // 解决：管理员审核附件前无法下载查看内容的问题
    // =====================================================================

    @SuppressWarnings("unchecked")
    @Test
    void adminDownload_canDownloadPending() {
        // 管理员可下载 PENDING 附件用于审核
        TeamMediaEntity m = media(10, "PENDING", 5);
        when(mediaService.findById(10)).thenReturn(m);
        when(fileStorageUtil.loadFile(anyString())).thenReturn(java.nio.file.Paths.get("/tmp/x"));

        ResponseEntity<?> resp = action.adminDownload(10);
        assertEquals(200, resp.getStatusCodeValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    void adminDownload_canDownloadRejected() {
        TeamMediaEntity m = media(10, "REJECTED", 5);
        when(mediaService.findById(10)).thenReturn(m);
        when(fileStorageUtil.loadFile(anyString())).thenReturn(java.nio.file.Paths.get("/tmp/x"));

        ResponseEntity<?> resp = action.adminDownload(10);
        assertEquals(200, resp.getStatusCodeValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    void adminDownload_mediaWithoutTeamId_returns404() {
        TeamMediaEntity m = media(10, "PENDING", null);
        when(mediaService.findById(10)).thenReturn(m);

        ResponseEntity<?> resp = action.adminDownload(10);
        assertEquals(404, resp.getStatusCodeValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    void ownerDownload_teamOwner_canDownloadOwnPending() {
        // team 5 的负责人 user 5 可下载自己团队的 PENDING 附件
        UserEntity owner = user(5, 1);
        mockTeamUser(5, 5); // user 5 → team 5
        TeamMediaEntity m = media(10, "PENDING", 5);
        when(mediaService.findById(10)).thenReturn(m);
        when(fileStorageUtil.loadFile(anyString())).thenReturn(java.nio.file.Paths.get("/tmp/x"));

        ResponseEntity<?> resp = action.ownerDownload(10, req(owner));
        assertEquals(200, resp.getStatusCodeValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    void ownerDownload_otherTeam_returns403() {
        // user 5 属于 team 5，但 media 属于 team 99 → 拒绝
        UserEntity owner = user(5, 1);
        mockTeamUser(5, 5);
        TeamMediaEntity m = media(10, "PENDING", 99);
        when(mediaService.findById(10)).thenReturn(m);

        ResponseEntity<?> resp = action.ownerDownload(10, req(owner));
        assertEquals(403, resp.getStatusCodeValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    void ownerDownload_admin_canDownloadAnyPending() {
        // 管理员（level=0）跳过归属校验，可下任意团队的 PENDING
        UserEntity admin = user(1, 0);
        TeamMediaEntity m = media(10, "PENDING", 99);
        when(mediaService.findById(10)).thenReturn(m);
        when(fileStorageUtil.loadFile(anyString())).thenReturn(java.nio.file.Paths.get("/tmp/x"));

        ResponseEntity<?> resp = action.ownerDownload(10, req(admin));
        assertEquals(200, resp.getStatusCodeValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    void ownerDownload_mediaNotFound_returns404() {
        UserEntity owner = user(5, 1);
        when(mediaService.findById(999)).thenReturn(null);

        ResponseEntity<?> resp = action.ownerDownload(999, req(owner));
        assertEquals(404, resp.getStatusCodeValue());
    }

    // =====================================================================
    // Bug 1: 受控图片读取接口（管理员/owner 可看任意状态，匿名仅 PUBLISHED）
    // 解决 Item 4 把 PENDING 移到 images_pending/ 后管理员无法预览的问题
    // =====================================================================

    @Test
    void serveImage_admin_canViewPending() throws Exception {
        // 已认证管理员可查看任意状态图片。
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("test-img", ".jpg");
        UserEntity admin = user(1, 0);
        when(imageService.findById(10)).thenReturn(image(10, "PENDING", 5, "images_pending/x.jpg"));
        when(fileStorageUtil.loadFile("images_pending/x.jpg")).thenReturn(tmp);

        ResponseEntity<?> resp = action.serveImage(10, req(admin));
        assertEquals(200, resp.getStatusCodeValue());
        // exy v5 Item 1：管理员预览 PENDING 图必须 no-store（审核中内容不进缓存）
        assertEquals("no-store", resp.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        java.nio.file.Files.delete(tmp);
    }

    @Test
    void serveImage_owner_canViewOwnPending() throws Exception {
        // 已认证团队负责人可查看自己 team 的 PENDING 图片。
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("test-img", ".jpg");
        UserEntity owner = user(5, 1);
        mockTeamUser(5, 5); // user 5 → team 5
        when(imageService.findById(10)).thenReturn(image(10, "PENDING", 5, "images_pending/x.jpg"));
        when(fileStorageUtil.loadFile("images_pending/x.jpg")).thenReturn(tmp);

        ResponseEntity<?> resp = action.serveImage(10, req(owner));
        assertEquals(200, resp.getStatusCodeValue());
        // exy v5 Item 1：owner 预览 PENDING 图必须 no-store
        assertEquals("no-store", resp.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        java.nio.file.Files.delete(tmp);
    }

    @Test
    void serveImage_otherTeam_returns404() {
        // user 5 属于 team 5，但图片属于 team 99 → 非所属负责人 → 视为匿名 → 非 PUBLISHED 返回 404
        UserEntity owner = user(5, 1);
        mockTeamUser(5, 5);
        when(imageService.findById(10)).thenReturn(image(10, "PENDING", 99, "images_pending/x.jpg"));

        ResponseEntity<?> resp = action.serveImage(10, req(owner));
        assertEquals(404, resp.getStatusCodeValue());
    }

    @Test
    void serveImage_anonymous_canViewPublishedOfPublishedTeam() throws Exception {
        // 匿名用户（无 token）可看 PUBLISHED 图片（PUBLISHED team）
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("test-img", ".jpg");
        MockHttpServletRequest request = new MockHttpServletRequest(); // 无 user attribute，无 token
        TeamEntity team = new TeamEntity();
        team.setId(5);
        team.setStatus(TeamEntity.Status.PUBLISHED);
        when(imageService.findById(10)).thenReturn(image(10, "PUBLISHED", 5, "images/x.jpg"));
        when(teamMapper.findById(5)).thenReturn(team);
        when(fileStorageUtil.loadFile("images/x.jpg")).thenReturn(tmp);

        ResponseEntity<?> resp = action.serveImage(10, request);
        assertEquals(200, resp.getStatusCodeValue());
        // exy v5 Item 1：匿名访问 PUBLISHED 图走缓存路径（private 仅浏览器缓存，max-age=3600）
        assertEquals("private, max-age=3600", resp.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        java.nio.file.Files.delete(tmp);
    }

    @Test
    void serveImage_anonymous_pending_returns404() {
        // 匿名看 PENDING → 拒绝（核心安全属性：PENDING 不泄露）
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(imageService.findById(10)).thenReturn(image(10, "PENDING", 5, "images_pending/x.jpg"));

        ResponseEntity<?> resp = action.serveImage(10, request);
        assertEquals(404, resp.getStatusCodeValue());
    }

    @Test
    void serveImage_authorizationHeader_adminCanView() throws Exception {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("test-img", ".jpg");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer admin-token");
        UserEntity admin = user(1, 0);
        when(tokenService.verifyAndGetUserId("admin-token")).thenReturn(1);
        when(userService.findUserById(1)).thenReturn(admin);
        when(imageService.findById(10)).thenReturn(image(10, "PENDING", 5, "images_pending/x.jpg"));
        when(fileStorageUtil.loadFile("images_pending/x.jpg")).thenReturn(tmp);

        ResponseEntity<?> resp = action.serveImage(10, request);
        assertEquals(200, resp.getStatusCodeValue());
        java.nio.file.Files.delete(tmp);
    }

    @Test
    void serveImage_queryTokenIsIgnoredAndCannotAuthorizePrivateImage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("token", "admin-token");
        when(imageService.findById(10)).thenReturn(image(10, "PENDING", 5, "images_pending/x.jpg"));

        ResponseEntity<?> resp = action.serveImage(10, request);
        assertEquals(404, resp.getStatusCodeValue());
        verifyNoInteractions(tokenService, userService);
    }

    @Test
    void serveImage_invalidHeaderToken_treatedAsAnonymous() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bad-token");
        when(tokenService.verifyAndGetUserId("bad-token")).thenThrow(new RuntimeException("invalid"));
        when(imageService.findById(10)).thenReturn(image(10, "PENDING", 5, "images_pending/x.jpg"));

        ResponseEntity<?> resp = action.serveImage(10, request);
        assertEquals(404, resp.getStatusCodeValue());
    }

    @Test
    void serveImage_imageNotFound_returns404() {
        when(imageService.findById(999)).thenReturn(null);
        ResponseEntity<?> resp = action.serveImage(999, new MockHttpServletRequest());
        assertEquals(404, resp.getStatusCodeValue());
    }

    // =====================================================================
    // 辅助方法
    // =====================================================================

    private TeamPageImageEntity image(int id, String status, int teamId, String imageUrl) {
        TeamPageImageEntity img = new TeamPageImageEntity();
        img.setId(id);
        img.setStatus(status);
        img.setTeamId(teamId);
        img.setImageUrl(imageUrl);
        return img;
    }

    private TeamMediaEntity media(int id, String status, Integer teamId) {
        TeamMediaEntity m = new TeamMediaEntity();
        m.setId(id);
        m.setStatus(status);
        m.setTeamId(teamId);
        m.setFilename("test.mp4");
        m.setRelativePath("media/test.mp4");
        m.setMimeType("video/mp4");
        m.setFileSize(100L);
        return m;
    }

    private UserEntity user(int id, int level) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setLevel(level);
        return u;
    }

    private MockHttpServletRequest req(UserEntity u) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(com.vihu.ganlu.security.AuthInterceptor.CURRENT_USER_ATTRIBUTE, u);
        return request;
    }

    private void inject(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
