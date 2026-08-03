package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.PublicImageUploadInfo;
import com.vihu.ganlu.entitys.TeamPageImageEntity;
import com.vihu.ganlu.mappers.TeamMediaMapper;
import com.vihu.ganlu.mappers.TeamPageImageMapper;
import com.vihu.ganlu.service.TeamPageImageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
@Service
public class TeamPageImageServiceImpl implements TeamPageImageService {
    private final TeamPageImageMapper teamPageImageMapper;
    private final TeamMediaMapper teamMediaMapper;
    private final PublicImageLifecycleService imageLifecycleService;

    public TeamPageImageServiceImpl(
            TeamPageImageMapper teamPageImageMapper,
            TeamMediaMapper teamMediaMapper,
            PublicImageLifecycleService imageLifecycleService) {
        this.teamPageImageMapper = teamPageImageMapper;
        this.teamMediaMapper = teamMediaMapper;
        this.imageLifecycleService = imageLifecycleService;
    }

    @Override
    @Transactional
    public int insertTeamImage(TeamPageImageEntity e) {
        requireImageUpload(e);
        if (e.getStatus() == null || e.getStatus().trim().isEmpty()) e.setStatus("PENDING");
        boolean published = "PUBLISHED".equals(e.getStatus());
        String imagePath = published
                ? imageLifecycleService.promote(e.getImageUploadUserId(), e.getImageUploadToken())
                : imageLifecycleService.promotePrivate(e.getImageUploadUserId(), e.getImageUploadToken());
        e.setImageUrl(imagePath);
        int inserted = teamPageImageMapper.insertTeamImage(e);
        if (inserted != 1) throw new IllegalStateException("保存团队图片记录失败");
        return inserted;
    }

    @Override
    public List<TeamPageImageEntity> findAllImages(int id) {
        return teamPageImageMapper.findAllImages(id);
    }

    @Override
    @Transactional
    public int deleteTeamPageImageByIds(List<Integer> ids) {
        List<TeamPageImageEntity> existing = teamPageImageMapper.findByIds(ids);
        int deleted = teamPageImageMapper.deleteTeamPageImageByIds(ids);
        if (deleted > 0) deleteFilesAfterCommit(existing);
        return deleted;
    }

    @Override
    @Transactional
    public int deleteTeamPageImageByIdsAndUserId(List<Integer> ids, Integer userId) {
        List<TeamPageImageEntity> existing = teamPageImageMapper.findByIdsAndUserId(ids, userId);
        int deleted = teamPageImageMapper.deleteTeamPageImageByIdsAndUserId(ids, userId);
        if (deleted > 0) deleteFilesAfterCommit(existing);
        return deleted;
    }

    @Override
    public PublicImageUploadInfo stageTeamImage(MultipartFile imageFile, int uploaderUserId) {
        return imageLifecycleService.stage(imageFile, uploaderUserId);
    }

    @Override
    public void cancelStagedTeamImage(String token, int uploaderUserId) {
        imageLifecycleService.cancel(uploaderUserId, token);
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
        TeamPageImageEntity existing = teamPageImageMapper.findByIdForUpdate(id);
        if (existing == null) return false;
        int updated = teamPageImageMapper.archiveById(id);
        if (updated != 1) throw new IllegalStateException("归档团队图片失败");
        moveByStatus(existing, "ARCHIVED");
        if (existing.getTeamId() != null) {
            teamMediaMapper.archiveByRelated("IMAGE", id, existing.getTeamId());
        }
        return true;
    }

    @Override
    @Transactional
    public boolean archiveByIdAndTeamId(int id, int teamId) {
        TeamPageImageEntity existing = teamPageImageMapper.findByIdForUpdate(id);
        if (existing == null || !Integer.valueOf(teamId).equals(existing.getTeamId())) return false;
        int updated = teamPageImageMapper.archiveByIdAndTeamId(id, teamId);
        if (updated != 1) throw new IllegalStateException("归档团队图片失败");
        moveByStatus(existing, "ARCHIVED");
        teamMediaMapper.archiveByRelated("IMAGE", id, teamId);
        return true;
    }

    @Override
    @Transactional
    public boolean updateStatus(int id, String status, String rejectReason) {
        requireStatus(status);
        TeamPageImageEntity existing = teamPageImageMapper.findByIdForUpdate(id);
        if (existing == null) return false;
        String movedPath = imageLifecycleService.moveManagedImage(
                existing.getImageUrl(), "PUBLISHED".equals(status));
        if (!movedPath.equals(existing.getImageUrl())
                && teamPageImageMapper.updateImageUrl(id, movedPath) != 1) {
            throw new IllegalStateException("同步团队图片路径失败");
        }
        if (teamPageImageMapper.updateImageStatus(id, status, rejectReason) != 1) {
            throw new IllegalStateException("更新团队图片审核状态失败");
        }
        if (existing.getTeamId() != null
                && ("REJECTED".equals(status) || "ARCHIVED".equals(status))) {
            teamMediaMapper.updateStatusByRelated("IMAGE", id, status, existing.getTeamId());
        }
        return true;
    }

    @Override
    @Transactional
    public boolean purgeById(int id) {
        TeamPageImageEntity existing = teamPageImageMapper.findByIdForUpdate(id);
        if (existing == null) return false;
        if (teamPageImageMapper.purgeById(id) != 1) {
            throw new IllegalStateException("彻底删除团队图片记录失败");
        }
        imageLifecycleService.deletePublicImageAfterCommit(existing.getImageUrl());
        return true;
    }

    private void requireImageUpload(TeamPageImageEntity entity) {
        if (entity == null || entity.getImageUploadUserId() == null
                || entity.getImageUploadToken() == null
                || entity.getImageUploadToken().trim().isEmpty()) {
            throw new IllegalArgumentException("请先上传并暂存团队图片");
        }
    }

    private void deleteFilesAfterCommit(List<TeamPageImageEntity> images) {
        if (images == null) return;
        for (TeamPageImageEntity image : images) {
            imageLifecycleService.deletePublicImageAfterCommit(image.getImageUrl());
        }
    }

    private void moveByStatus(TeamPageImageEntity image, String status) {
        String movedPath = imageLifecycleService.moveManagedImage(
                image.getImageUrl(), "PUBLISHED".equals(status));
        if (!movedPath.equals(image.getImageUrl())
                && teamPageImageMapper.updateImageUrl(image.getId(), movedPath) != 1) {
            throw new IllegalStateException("同步团队图片路径失败");
        }
    }

    private void requireStatus(String status) {
        if (!java.util.Arrays.asList("PENDING", "PUBLISHED", "REJECTED", "ARCHIVED").contains(status)) {
            throw new IllegalArgumentException("无效的团队图片状态");
        }
    }
}
