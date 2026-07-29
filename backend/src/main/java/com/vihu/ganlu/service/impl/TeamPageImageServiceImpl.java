package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.TeamPageImageEntity;
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

    public String uploadTeamImage(MultipartFile imageFile){
        try{
            String thumbnailPath = fileStorageUtil.storeFile(imageFile, "images");
            return thumbnailPath;

        }catch (RuntimeException e) {
            throw new RuntimeException("文件存储失败", e);
        }
    }
}
