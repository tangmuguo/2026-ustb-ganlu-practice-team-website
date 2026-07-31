package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.TeamPageImageEntity;
import com.vihu.ganlu.mappers.TeamMediaMapper;
import com.vihu.ganlu.mappers.TeamPageImageMapper;
import com.vihu.ganlu.service.TeamPageImageService;
import com.vihu.ganlu.utils.FileStorageUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.List;
@Service
public class TeamPageImageServiceImpl implements TeamPageImageService {
    @Resource
    TeamPageImageMapper teamPageImageMapper;
    @Resource
    TeamMediaMapper teamMediaMapper;
    @Resource
    FileStorageUtil fileStorageUtil;

    @Value("${file.upload-dir}")
    private String uploadDir;

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
            String thumbnailPath = fileStorageUtil.storeFile(imageFile, "images");
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
    public boolean archiveById(int id) {
        TeamPageImageEntity e = teamPageImageMapper.findById(id);
        int n = teamPageImageMapper.archiveById(id);
        if (n > 0 && e != null && e.getTeamId() != null) {
            teamMediaMapper.archiveByRelated("IMAGE", id, e.getTeamId()); // 级联归档关联 media
        }
        return n > 0;
    }

    @Override
    public boolean archiveByIdAndTeamId(int id, int teamId) {
        int n = teamPageImageMapper.archiveByIdAndTeamId(id, teamId);
        if (n > 0) {
            teamMediaMapper.archiveByRelated("IMAGE", id, teamId); // 级联归档关联 media
        }
        return n > 0;
    }

    @Override
    public boolean updateStatus(int id, String status, String rejectReason) {
        TeamPageImageEntity e = teamPageImageMapper.findById(id);
        int n = teamPageImageMapper.updateImageStatus(id, status, rejectReason);
        if (n > 0 && e != null && e.getTeamId() != null) {
            teamMediaMapper.updateStatusByRelated("IMAGE", id, status, e.getTeamId()); // 级联同步 media 状态
        }
        return n > 0;
    }
}
