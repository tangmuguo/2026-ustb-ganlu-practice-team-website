package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.TeamPageImageEntity;
import com.vihu.ganlu.mappers.TeamMediaMapper;
import com.vihu.ganlu.mappers.TeamPageImageMapper;
import com.vihu.ganlu.utils.FileStorageUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * TeamPageImageServiceImpl 单元测试 — JUnit 5 + Mockito（纯单元，不启动 Spring 容器）。
 * 覆盖 exy v5 Item 1/2：发布前置文件校验（与 serveImage 同路径）、发布不搬移文件。
 */
class TeamPageImageServiceImplTests {
    private TeamPageImageServiceImpl service;
    private TeamPageImageMapper imageMapper;
    private TeamMediaMapper mediaMapper;
    private FileStorageUtil fileStorageUtil;

    @BeforeEach
    void setUp() {
        imageMapper = mock(TeamPageImageMapper.class);
        mediaMapper = mock(TeamMediaMapper.class);
        fileStorageUtil = mock(FileStorageUtil.class);
        service = new TeamPageImageServiceImpl();
        // 字段为包可见（@Resource 注入点），测试同包直接赋值
        service.teamPageImageMapper = imageMapper;
        service.teamMediaMapper = mediaMapper;
        service.fileStorageUtil = fileStorageUtil;
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
    void updateStatus_publishedFileExists_passesAndKeepsImageUrl() throws Exception {
        // exy v5 Item 1/2：发布仅校验文件存在 + 改 DB 状态——文件不搬移，imageUrl 不变
        Path tmp = Files.createTempFile("test-img", ".jpg");
        TeamPageImageEntity img = image(10, "images_pending/x.jpg");
        when(imageMapper.findById(10)).thenReturn(img);
        when(fileStorageUtil.loadFile("images_pending/x.jpg")).thenReturn(tmp);
        when(imageMapper.updateImageStatus(10, "PUBLISHED", null)).thenReturn(1);

        assertTrue(service.updateStatus(10, "PUBLISHED", null));
        assertEquals("images_pending/x.jpg", img.getImageUrl()); // 无文件搬移，路径不变
        verify(imageMapper).updateImageStatus(10, "PUBLISHED", null);
        Files.delete(tmp);
    }

    @Test
    void updateStatus_publishedMissingFile_throws() {
        // exy v5 Item 2：发布前文件不存在 → IllegalStateException，DB 状态不变
        TeamPageImageEntity img = image(10, "images_pending/x.jpg");
        when(imageMapper.findById(10)).thenReturn(img);
        when(fileStorageUtil.loadFile("images_pending/x.jpg"))
                .thenReturn(Paths.get("/nonexistent/dir/x.jpg"));

        assertThrows(IllegalStateException.class,
                () -> service.updateStatus(10, "PUBLISHED", null));
        verify(imageMapper, never()).updateImageStatus(anyInt(), anyString(), any());
    }

    @Test
    void updateStatus_legacyImagesPrefixFileExists_passes() throws Exception {
        // 修复项：校验路径与 serveImage 一致（直接用 DB 的 imageUrl 而非按 basename 重建）——
        // 历史 images/ 前缀行（物理文件仍在 images/）也能正常发布，不再被误拒
        Path tmp = Files.createTempFile("test-img", ".jpg");
        TeamPageImageEntity img = image(11, "images/hist.jpg");
        when(imageMapper.findById(11)).thenReturn(img);
        when(fileStorageUtil.loadFile("images/hist.jpg")).thenReturn(tmp);
        when(imageMapper.updateImageStatus(11, "PUBLISHED", null)).thenReturn(1);

        assertTrue(service.updateStatus(11, "PUBLISHED", null));
        Files.delete(tmp);
    }

    @Test
    void updateStatus_rejectDoesNotRequireFile() {
        // 驳回不强制文件存在（缺文件驳回不影响安全）
        TeamPageImageEntity img = image(12, "images_pending/gone.jpg");
        when(imageMapper.findById(12)).thenReturn(img);
        when(imageMapper.updateImageStatus(12, "REJECTED", "原因")).thenReturn(1);
        when(mediaMapper.updateStatusByRelated("IMAGE", 12, "REJECTED", 5)).thenReturn(1);

        assertTrue(service.updateStatus(12, "REJECTED", "原因"));
        verify(imageMapper).updateImageStatus(12, "REJECTED", "原因");
        verify(mediaMapper).updateStatusByRelated("IMAGE", 12, "REJECTED", 5);
    }
}
