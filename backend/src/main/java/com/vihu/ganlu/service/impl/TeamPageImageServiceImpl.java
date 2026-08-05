package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.TeamPageImageEntity;
import com.vihu.ganlu.mappers.TeamMediaMapper;
import com.vihu.ganlu.mappers.TeamPageImageMapper;
import com.vihu.ganlu.service.TeamPageImageService;
import com.vihu.ganlu.utils.FileStorageUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.nio.file.Files;
import java.util.List;

@Slf4j
@Service
public class TeamPageImageServiceImpl implements TeamPageImageService {
    @Resource
    TeamPageImageMapper teamPageImageMapper;
    @Resource
    TeamMediaMapper teamMediaMapper;
    @Resource
    FileStorageUtil fileStorageUtil;

    // 团队风采图专用目录（不映射静态资源 /images/**）：所有状态（PENDING/PUBLISHED/REJECTED/ARCHIVED）
    // 的物理文件都永驻于此目录。访问统一走 serveImage 接口按 status 鉴权：
    //   - 匿名仅能访问 PUBLISHED + team PUBLISHED 的图
    //   - PENDING/REJECTED/ARCHIVED 图匿名访问必然 404
    // exy v5 Item 1：废弃"发布时搬文件到 images/ 静态目录"的旧设计——
    // 文件搬移不可进事务，进程崩溃/补偿失败时 PENDING 图滞留公开目录会泄露。
    // 不搬移 = 没有泄露窗口，serveImage 的 status 校验是唯一访问控制。
    private static final String PENDING_DIR = "images_pending";

    @Override
    public int insertTeamImage(TeamPageImageEntity e) {
        return teamPageImageMapper.insertTeamImage(e);
    }

    @Override
    public List<TeamPageImageEntity> findAllImages(int id) {
        return teamPageImageMapper.findAllImages(id);
    }

    @Override
    public int deleteTeamPageImageByIds(List<Integer> ids) {
        return teamPageImageMapper.deleteTeamPageImageByIds(ids);
    }

    @Override
    public int deleteTeamPageImageByIdsAndUserId(List<Integer> ids, Integer userId) {
        return teamPageImageMapper.deleteTeamPageImageByIdsAndUserId(ids, userId);
    }

    public String uploadTeamImage(MultipartFile imageFile) {
        try {
            // 新上传图片默认存私有目录 images_pending/，审核通过后再 move 到 images/。
            // images_pending/ 不被 CorsConfig 映射为静态资源，PENDING/REJECTED/ARCHIVED 图片物理隔离。
            String thumbnailPath = fileStorageUtil.storeFile(imageFile, PENDING_DIR);
            return thumbnailPath;
        } catch (RuntimeException e) {
            throw new RuntimeException("文件存储失败", e);
        }
    }

    @Override
    public List<TeamPageImageEntity> findByTeamId(int teamId) {
        return teamPageImageMapper.findByTeamId(teamId);
    }

    @Override
    public List<TeamPageImageEntity> findByTeamIdAndStatus(int teamId, String status) {
        return teamPageImageMapper.findByTeamIdAndStatus(teamId, status);
    }

    @Override
    public TeamPageImageEntity findById(int id) {
        return teamPageImageMapper.findById(id);
    }

    @Override
    @Transactional
    public boolean archiveById(int id) {
        TeamPageImageEntity e = teamPageImageMapper.findById(id);
        if (e == null) {
            return false;
        }
        // exy v5 Item 1：不再搬移文件（所有状态永驻 images_pending/），只改 DB 状态
        int n = teamPageImageMapper.archiveById(id);
        if (n > 0 && e.getTeamId() != null) {
            teamMediaMapper.archiveByRelated("IMAGE", id, e.getTeamId()); // 级联归档关联 media
        }
        return n > 0;
    }

    @Override
    @Transactional
    public boolean archiveByIdAndTeamId(int id, int teamId) {
        TeamPageImageEntity e = teamPageImageMapper.findById(id);
        if (e == null || !Integer.valueOf(teamId).equals(e.getTeamId())) {
            return false;
        }
        int n = teamPageImageMapper.archiveByIdAndTeamId(id, teamId);
        if (n > 0) {
            teamMediaMapper.archiveByRelated("IMAGE", id, teamId); // 级联归档关联 media
        }
        return n > 0;
    }

    @Override
    @Transactional
    public boolean updateStatus(int id, String status, String rejectReason) {
        TeamPageImageEntity e = teamPageImageMapper.findById(id);
        if (e == null) {
            return false;
        }
        // exy v5 Item 2：发布（PUBLISHED）前确认物理文件存在，避免发布一个公开页 404 的资源。
        // 归档/驳回不强制——缺文件归档不影响安全，只记 warn（由调用方处理）。
        // 校验路径与 serveImage 完全一致（loadFile(imageUrl)，不按 basename 重建）：
        // 历史数据（images/ 前缀、裸文件名）与迁移中间态都能被正确判定，不会误拒。
        if ("PUBLISHED".equals(status)) {
            String imageUrl = e.getImageUrl();
            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                throw new IllegalStateException("图片 imageUrl 为空，无法发布: id=" + id);
            }
            boolean fileExists;
            try {
                fileExists = Files.exists(fileStorageUtil.loadFile(imageUrl));
            } catch (Exception checkEx) {
                throw new IllegalStateException("发布前检查图片文件失败: id=" + id + ", reason=" + checkEx.getMessage(), checkEx);
            }
            if (!fileExists) {
                throw new IllegalStateException("图片源文件不存在，无法发布: id=" + id + ", url=" + imageUrl);
            }
        }
        // exy v5 Item 1：不再搬移文件（所有状态永驻 images_pending/），只改 DB 状态
        int n = teamPageImageMapper.updateImageStatus(id, status, rejectReason);
        if (n > 0) {
            // 父内容被驳回/归档时，级联隐藏关联附件；父内容发布时不自动提升附件，
            // 附件需保持自身的独立审核结果，避免已驳回/已归档附件被复活。
            if (e.getTeamId() != null
                    && ("REJECTED".equals(status) || "ARCHIVED".equals(status))) {
                teamMediaMapper.updateStatusByRelated("IMAGE", id, status, e.getTeamId());
            }
        }
        return n > 0;
    }
}
