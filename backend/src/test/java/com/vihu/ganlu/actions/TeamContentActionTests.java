package com.vihu.ganlu.actions;

import com.vihu.ganlu.entitys.TeamEntity;
import com.vihu.ganlu.entitys.TeamMediaEntity;
import com.vihu.ganlu.entitys.TeamPageImageEntity;
import com.vihu.ganlu.entitys.TeamPageWordEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.mappers.TeamMapper;
import com.vihu.ganlu.service.TeamMediaService;
import com.vihu.ganlu.service.TeamPageImageService;
import com.vihu.ganlu.service.TeamPageWordService;
import com.vihu.ganlu.utils.FileStorageUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @BeforeEach
    void setUp() {
        imageService = mock(TeamPageImageService.class);
        wordService = mock(TeamPageWordService.class);
        mediaService = mock(TeamMediaService.class);
        fileStorageUtil = mock(FileStorageUtil.class);
        teamMapper = mock(TeamMapper.class);
        action = new TeamContentAction();
        // 通过反射注入 @Resource 字段
        inject(action, "teamPageImageService", imageService);
        inject(action, "teamPageWordService", wordService);
        inject(action, "teamMediaService", mediaService);
        inject(action, "fileStorageUtil", fileStorageUtil);
        inject(action, "teamMapper", teamMapper);
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
    void adminPublish_image_success() {
        when(imageService.updateStatus(10, "PUBLISHED", null)).thenReturn(true);

        ResponseEntity<?> resp = action.adminPublish("image", 10);
        assertEquals(200, resp.getStatusCodeValue());
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

    @SuppressWarnings("unchecked")
    @Test
    void getPublicTeamContent_onlyPublished() {
        TeamEntity team = new TeamEntity();
        team.setId(5);
        team.setStatus(TeamEntity.Status.PUBLISHED);
        when(teamMapper.findById(5)).thenReturn(team);
        when(imageService.findByTeamIdAndStatus(5, "PUBLISHED")).thenReturn(Collections.emptyList());
        when(wordService.findByTeamIdAndStatus(5, "PUBLISHED")).thenReturn(Collections.emptyList());
        when(mediaService.findByStatus(5, "PUBLISHED")).thenReturn(Collections.emptyList());

        ResponseEntity<?> resp = action.getPublicTeamContent(5);
        assertEquals(200, resp.getStatusCodeValue());
        verify(imageService).findByTeamIdAndStatus(5, "PUBLISHED");
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
        // Mock fileStorageUtil.isAllowedImage 抛异常（空文件校验失败）
        when(fileStorageUtil.isAllowedImage(emptyFile))
                .thenThrow(new IllegalArgumentException("文件为空"));

        ResponseEntity<?> resp = action.uploadMember(emptyFile, "caption", "content", null, req(user));
        assertEquals(400, resp.getStatusCodeValue());
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
        when(fileStorageUtil.extractExtension("test.mp4")).thenReturn("mp4");
        when(fileStorageUtil.isAllowedVideo(file)).thenReturn(null);

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
        when(fileStorageUtil.extractExtension("test.doc")).thenReturn("doc");
        when(fileStorageUtil.isAllowedDocument(file)).thenReturn(null);

        // 父内容属于另一个 team
        TeamPageWordEntity parent = new TeamPageWordEntity();
        parent.setId(1);
        parent.setTeamId(99); // 不是 10
        parent.setStatus("PUBLISHED");
        when(wordService.findById(1)).thenReturn(parent);

        ResponseEntity<?> resp = action.uploadMedia(file, "WORD", 1, req(user));
        assertEquals(400, resp.getStatusCodeValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    void uploadMedia_onlyRelatedTypeProvided_returns400() {
        // relatedType 与 relatedId 只传一个，应拒绝（避免半关联记录）
        UserEntity user = user(5, 1);
        mockTeamUser(5, 10);
        MockMultipartFile file = new MockMultipartFile("file", "test.mp4",
                "video/mp4", new byte[]{0, 0, 0, 0, 'f', 't', 'y', 'p'});
        when(fileStorageUtil.extractExtension("test.mp4")).thenReturn("mp4");
        when(fileStorageUtil.isAllowedVideo(file)).thenReturn(null);

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
    // 辅助方法
    // =====================================================================

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
