package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.PublicImageUploadInfo;
import com.vihu.ganlu.entitys.TeamPageImageEntity;
import com.vihu.ganlu.mappers.TeamPageImageMapper;
import com.vihu.ganlu.service.TeamPageImageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
@Service
public class TeamPageImageServiceImpl implements TeamPageImageService {
    private final TeamPageImageMapper teamPageImageMapper;
    private final PublicImageLifecycleService imageLifecycleService;

    public TeamPageImageServiceImpl(
            TeamPageImageMapper teamPageImageMapper,
            PublicImageLifecycleService imageLifecycleService) {
        this.teamPageImageMapper = teamPageImageMapper;
        this.imageLifecycleService = imageLifecycleService;
    }

    @Override
    @Transactional
    public int insertTeamImage(TeamPageImageEntity e) {
        requireImageUpload(e);
        String imagePath = imageLifecycleService.promote(
                e.getImageUploadUserId(), e.getImageUploadToken());
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
}
