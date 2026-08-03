package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.TeamPageImageEntity;
import com.vihu.ganlu.mappers.TeamPageImageMapper;
import com.vihu.ganlu.service.TeamPageImageService;
import com.vihu.ganlu.utils.FileStorageUtil;
import com.vihu.ganlu.utils.PublicImageValidator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
@Service
public class TeamPageImageServiceImpl implements TeamPageImageService {
    private final TeamPageImageMapper teamPageImageMapper;
    private final FileStorageUtil fileStorageUtil;
    private final PublicImageValidator publicImageValidator;

    public TeamPageImageServiceImpl(
            TeamPageImageMapper teamPageImageMapper,
            FileStorageUtil fileStorageUtil,
            PublicImageValidator publicImageValidator) {
        this.teamPageImageMapper = teamPageImageMapper;
        this.fileStorageUtil = fileStorageUtil;
        this.publicImageValidator = publicImageValidator;
    }

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
        PublicImageValidator.ValidatedImage validated = publicImageValidator.validate(imageFile);
        return fileStorageUtil.storeFile(imageFile, "images", validated.getExtension());
    }
}
