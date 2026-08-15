package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.PublicImageUploadInfo;
import com.vihu.ganlu.entitys.TeamPageImageEntity;
import com.vihu.ganlu.mappers.TeamMediaMapper;
import com.vihu.ganlu.mappers.TeamPageImageMapper;
import com.vihu.ganlu.service.TeamPageImageService;
import com.vihu.ganlu.security.file.ChildPrivacyGateService;
import com.vihu.ganlu.security.file.PrivacyAssetType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
@Service
public class TeamPageImageServiceImpl implements TeamPageImageService {
    private final TeamPageImageMapper teamPageImageMapper;
    private final TeamMediaMapper teamMediaMapper;
    private final PublicImageLifecycleService imageLifecycleService;
    private final ChildPrivacyGateService childPrivacyGateService;
    private final boolean securePublicationGate;

    /** Legacy isolated-test constructor. */
    public TeamPageImageServiceImpl(
            TeamPageImageMapper teamPageImageMapper,
            TeamMediaMapper teamMediaMapper,
            PublicImageLifecycleService imageLifecycleService) {
        this(teamPageImageMapper, teamMediaMapper, imageLifecycleService,
                new ChildPrivacyGateService(null), false);
    }

    @Autowired
    public TeamPageImageServiceImpl(
            TeamPageImageMapper teamPageImageMapper,
            TeamMediaMapper teamMediaMapper,
            PublicImageLifecycleService imageLifecycleService,
            ChildPrivacyGateService childPrivacyGateService) {
        this(teamPageImageMapper, teamMediaMapper, imageLifecycleService,
                childPrivacyGateService, true);
    }

    private TeamPageImageServiceImpl(
            TeamPageImageMapper teamPageImageMapper,
            TeamMediaMapper teamMediaMapper,
            PublicImageLifecycleService imageLifecycleService,
            ChildPrivacyGateService childPrivacyGateService,
            boolean securePublicationGate) {
        this.teamPageImageMapper = teamPageImageMapper;
        this.teamMediaMapper = teamMediaMapper;
        this.imageLifecycleService = imageLifecycleService;
        this.childPrivacyGateService = childPrivacyGateService;
        this.securePublicationGate = securePublicationGate;
    }

    @Override
    @Transactional
    public int insertTeamImage(TeamPageImageEntity e) {
        requireImageUpload(e);
        requireSupportedNewImageType(e);
        if (e.getStatus() == null || e.getStatus().trim().isEmpty()) e.setStatus("PENDING");
        boolean published = "PUBLISHED".equals(e.getStatus());
        if (published && securePublicationGate) {
            childPrivacyGateService.requirePublicationAllowed(
                    PrivacyAssetType.CHILD_PHOTO, null,
                    e.getUserId() == null ? e.getImageUploadUserId() : e.getUserId(), null);
        }
        String imagePath = published
                ? imageLifecycleService.promote(e.getImageUploadUserId(), e.getImageUploadToken())
                : imageLifecycleService.promotePrivate(e.getImageUploadUserId(), e.getImageUploadToken());
        e.setImageUrl(imagePath);
        // The lifecycle only returns after the actual bytes have passed the
        // scanner and image normalizer. Persist that state explicitly because
        // the additive SQL column is NOT NULL and defaults to fail-closed.
        e.setScanStatus("CLEAN");
        e.setScanDiagnosticStatus("CLEAN");
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
        List<Integer> lockedIds = normalizedIds(ids);
        List<TeamPageImageEntity> existing = teamPageImageMapper.findByIdsForUpdate(lockedIds);
        int deleted = teamPageImageMapper.deleteTeamPageImageByIds(lockedIds);
        if (deleted > 0) deleteFilesAfterCommit(existing);
        return deleted;
    }

    @Override
    @Transactional
    public int deleteTeamPageImageByIdsAndUserId(List<Integer> ids, Integer userId) {
        List<Integer> lockedIds = normalizedIds(ids);
        List<TeamPageImageEntity> existing = teamPageImageMapper.findByIdsAndUserIdForUpdate(lockedIds, userId);
        int deleted = teamPageImageMapper.deleteTeamPageImageByIdsAndUserId(lockedIds, userId);
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
        if ("PUBLISHED".equals(status) && securePublicationGate) {
            childPrivacyGateService.requirePublicationAllowed(
                    PrivacyAssetType.CHILD_PHOTO, existing.getId() == null ? null : existing.getId().longValue(),
                    existing.getUserId(), null);
        }
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

    /**
     * 新建记录仅保留团队成员照片和支教风采两类；地区照片（旧 type=3）
     * 仍可被历史查询读取，但不能再经任意写入路径新增。
     */
    private void requireSupportedNewImageType(TeamPageImageEntity entity) {
        Integer type = entity.getType();
        if (type == null || (type != TeamPageImageEntity.TYPE_MEMBER_PHOTO
                && type != TeamPageImageEntity.TYPE_TEACHING_STYLE_PHOTO)) {
            throw new IllegalArgumentException("无效的团队图片类型");
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

    private List<Integer> normalizedIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("图片编号不能为空");
        List<Integer> normalized = ids.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.toList());
        if (normalized.isEmpty()) throw new IllegalArgumentException("图片编号不能为空");
        return normalized;
    }
}
