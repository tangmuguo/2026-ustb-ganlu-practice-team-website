package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.TeamPageImageEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TeamPageImageService {
    int insertTeamImage(TeamPageImageEntity e);
    List<TeamPageImageEntity> findAllImages(int id);
    int deleteTeamPageImageByIds(List<Integer> ids);
    int deleteTeamPageImageByIdsAndUserId(List<Integer> ids, Integer userId);
    String uploadTeamImage(MultipartFile imageFile);
}
