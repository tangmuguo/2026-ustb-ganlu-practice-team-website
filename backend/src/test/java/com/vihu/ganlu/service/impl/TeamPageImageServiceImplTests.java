package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.TeamPageImageEntity;
import com.vihu.ganlu.mappers.TeamMediaMapper;
import com.vihu.ganlu.mappers.TeamPageImageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * TeamPageImageServiceImpl 单元测试 — JUnit 5 + Mockito（纯单元，不启动 Spring 容器）。
 * 覆盖图片审核状态与公开/私有文件生命周期的原子同步。
 */
class TeamPageImageServiceImplTests {
    private TeamPageImageServiceImpl service;
    private TeamPageImageMapper imageMapper;
    private TeamMediaMapper mediaMapper;
    private PublicImageLifecycleService imageLifecycleService;

    @BeforeEach
    void setUp() {
        imageMapper = mock(TeamPageImageMapper.class);
        mediaMapper = mock(TeamMediaMapper.class);
        imageLifecycleService = mock(PublicImageLifecycleService.class);
        service = new TeamPageImageServiceImpl(imageMapper, mediaMapper, imageLifecycleService);
    }

    private TeamPageImageEntity image(int id, String imageUrl) {
        TeamPageImageEntity img = new TeamPageImageEntity();
        img.setId(id);
        img.setImageUrl(imageUrl);
        img.setTeamId(5);
        img.setStatus("PENDING");
        return img;
    }

    @Test
    void insertTeamImage_rejectsObsoletePhotoTypeBeforePromotingFile() {
        TeamPageImageEntity image = stagedImage(3);

        assertThrows(IllegalArgumentException.class, () -> service.insertTeamImage(image));

        verifyNoInteractions(imageLifecycleService, imageMapper);
    }

    @Test
    void insertTeamImage_allowsMissingRemarkAndPassesNullToMapper() {
        TeamPageImageEntity image = stagedImage(TeamPageImageEntity.TYPE_TEACHING_STYLE_PHOTO);
        when(imageLifecycleService.promotePrivate(5, "staged-token"))
                .thenReturn("images_pending/a.jpg");
        when(imageMapper.insertTeamImage(any(TeamPageImageEntity.class))).thenReturn(1);

        assertEquals(1, service.insertTeamImage(image));

        verify(imageMapper).insertTeamImage(argThat(saved ->
                saved.getContent() == null
                        && Integer.valueOf(TeamPageImageEntity.TYPE_TEACHING_STYLE_PHOTO)
                        .equals(saved.getType())));
    }

    @Test
    void updateStatus_publishedMovesImageAndPersistsNewUrl() {
        TeamPageImageEntity img = image(10, "images_pending/x.jpg");
        when(imageMapper.findByIdForUpdate(10)).thenReturn(img);
        when(imageLifecycleService.moveManagedImage("images_pending/x.jpg", true))
                .thenReturn("images/x.jpg");
        when(imageMapper.updateImageUrl(10, "images/x.jpg")).thenReturn(1);
        when(imageMapper.updateImageStatus(10, "PUBLISHED", null)).thenReturn(1);

        assertTrue(service.updateStatus(10, "PUBLISHED", null));
        verify(imageLifecycleService).moveManagedImage("images_pending/x.jpg", true);
        verify(imageMapper).updateImageUrl(10, "images/x.jpg");
        verify(imageMapper).updateImageStatus(10, "PUBLISHED", null);
    }

    @Test
    void updateStatus_publishedMissingFile_propagatesAndDoesNotChangeDatabase() {
        TeamPageImageEntity img = image(10, "images_pending/x.jpg");
        when(imageMapper.findByIdForUpdate(10)).thenReturn(img);
        when(imageLifecycleService.moveManagedImage("images_pending/x.jpg", true))
                .thenThrow(new IllegalStateException("图片文件不存在"));

        assertThrows(IllegalStateException.class,
                () -> service.updateStatus(10, "PUBLISHED", null));
        verify(imageMapper, never()).updateImageUrl(anyInt(), anyString());
        verify(imageMapper, never()).updateImageStatus(anyInt(), anyString(), any());
    }

    @Test
    void updateStatus_alreadyPublicImage_doesNotRewriteUrl() {
        TeamPageImageEntity img = image(11, "images/hist.jpg");
        when(imageMapper.findByIdForUpdate(11)).thenReturn(img);
        when(imageLifecycleService.moveManagedImage("images/hist.jpg", true))
                .thenReturn("images/hist.jpg");
        when(imageMapper.updateImageStatus(11, "PUBLISHED", null)).thenReturn(1);

        assertTrue(service.updateStatus(11, "PUBLISHED", null));
        verify(imageMapper, never()).updateImageUrl(anyInt(), anyString());
        verify(imageMapper).updateImageStatus(11, "PUBLISHED", null);
    }

    @Test
    void updateStatus_rejectDoesNotRequireFile() {
        TeamPageImageEntity img = image(12, "images_pending/gone.jpg");
        when(imageMapper.findByIdForUpdate(12)).thenReturn(img);
        when(imageLifecycleService.moveManagedImage("images_pending/gone.jpg", false))
                .thenReturn("images_pending/gone.jpg");
        when(imageMapper.updateImageStatus(12, "REJECTED", "原因")).thenReturn(1);
        when(mediaMapper.updateStatusByRelated("IMAGE", 12, "REJECTED", 5)).thenReturn(1);

        assertTrue(service.updateStatus(12, "REJECTED", "原因"));
        verify(imageLifecycleService).moveManagedImage("images_pending/gone.jpg", false);
        verify(imageMapper).updateImageStatus(12, "REJECTED", "原因");
        verify(mediaMapper).updateStatusByRelated("IMAGE", 12, "REJECTED", 5);
    }

    private TeamPageImageEntity stagedImage(int type) {
        TeamPageImageEntity image = new TeamPageImageEntity();
        image.setImageUploadUserId(5);
        image.setImageUploadToken("staged-token");
        image.setType(type);
        image.setCaption("标题");
        return image;
    }
}
