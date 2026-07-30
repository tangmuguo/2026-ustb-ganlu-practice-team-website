package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.TeamMediaEntity;
import com.vihu.ganlu.mappers.TeamMediaMapper;
import com.vihu.ganlu.service.TeamMediaService;
import com.vihu.ganlu.utils.FileStorageUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.List;
@Service
public class TeamMediaServiceImpl implements TeamMediaService {
    @Resource
    TeamMediaMapper teamMediaMapper;
    @Resource
    FileStorageUtil fileStorageUtil;

    @Override
    public TeamMediaEntity uploadMedia(MultipartFile file, int uploaderId, int teamId,
                                       String relatedType, Integer relatedId) {
        String relativePath = fileStorageUtil.storeFile(file, "media");
        try {
            TeamMediaEntity entity = new TeamMediaEntity();
            entity.setFilename(file.getOriginalFilename());
            entity.setRelativePath(relativePath);
            entity.setMimeType(file.getContentType());
            entity.setFileSize(file.getSize());
            entity.setUploaderId(uploaderId);
            entity.setTeamId(teamId);
            entity.setRelatedType(relatedType);
            entity.setRelatedId(relatedId);
            entity.setStatus("PENDING");
            teamMediaMapper.insertTeamMedia(entity);
            return entity;
        } catch (Exception e) {
            fileStorageUtil.deleteFile(relativePath); // DB 失败清理孤立文件
            throw e;
        }
    }

    @Override
    public TeamMediaEntity findById(int id) {
        return teamMediaMapper.findById(id);
    }

    @Override
    public List<TeamMediaEntity> findByTeamId(int teamId) {
        return teamMediaMapper.findByTeamId(teamId);
    }

    @Override
    public List<TeamMediaEntity> findByStatus(int teamId, String status) {
        return teamMediaMapper.findByStatus(teamId, status);
    }

    @Override
    public boolean updateStatus(int id, String status, String rejectReason) {
        return teamMediaMapper.updateStatus(id, status, rejectReason) > 0;
    }

    @Override
    public boolean archiveByRelated(String relatedType, int relatedId) {
        return teamMediaMapper.archiveByRelated(relatedType, relatedId) > 0;
    }

    @Override
    public int deleteByIds(List<Integer> ids) {
        return teamMediaMapper.deleteByIds(ids);
    }

    @Override
    public int deleteByIdsAndUploader(List<Integer> ids, int uploaderId) {
        return teamMediaMapper.deleteByIdsAndUploader(ids, uploaderId);
    }
}
